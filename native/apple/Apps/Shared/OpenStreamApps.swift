import AVFoundation
import AVKit
import Network
import OpenStreamApple
import SwiftUI

@main
struct OpenStreamPlatformApp: App {
    @State private var player: AVPlayer?
    @State private var showDetails = false
    @State private var playbackLabels: [String] = []
    @State private var showPlaybackLabels = false
    @State private var offlineAvailable = false
    @State private var offlineBusy = false
    @State private var showLaunch = true
    @StateObject private var network = AppleNetworkStatus()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private let offlineStore = AppleOfflineMediaStore()

    var body: some Scene {
        WindowGroup {
            ZStack {
                OpenStreamRootView(
                    onPlay: playBundledDemo,
                    onDetails: { showDetails = true },
                    offlineLabel: offlineBusy ? "Saving…" : (offlineAvailable ? "Available offline" : "Make available offline"),
                    networkLabel: network.online ? "ONLINE" : "OFFLINE",
                    onOffline: toggleOfflineDemo
                )

                if let player {
                    VideoPlayer(player: player)
                        .ignoresSafeArea()
                        .overlay(alignment: .topLeading) {
                            Button {
                                player.pause()
                                self.player = nil
                            } label: {
                                Label("Close", systemImage: "xmark.circle.fill")
                                    .font(.headline)
                                    .padding(12)
                            }
                            .buttonStyle(.borderedProminent)
                            .padding(24)
                        }
                        .overlay(alignment: .topTrailing) {
                            if showPlaybackLabels, !playbackLabels.isEmpty {
                                Text(playbackLabels.joined(separator: "  •  "))
                                    .font(.caption.bold())
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 10)
                                    .background(.black.opacity(0.72), in: RoundedRectangle(cornerRadius: 8))
                                    .padding(24)
                            }
                        }
                        .transition(.opacity)
                }

                if showDetails {
                    detailsPanel
                }

                if showLaunch {
                    AppleOpenStreamLaunchView()
                        .transition(.opacity)
                        .zIndex(20)
                }
            }
            .animation(.easeInOut(duration: 0.22), value: player != nil)
            .task {
                offlineAvailable = await offlineStore.record(for: "openstream-demo") != nil
                try? await Task.sleep(for: .milliseconds(reduceMotion ? 220 : 1_300))
                if reduceMotion {
                    showLaunch = false
                } else {
                    withAnimation(.easeOut(duration: 0.22)) { showLaunch = false }
                }
            }
        }
    }

    private var detailsPanel: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("OpenStream")
                    .font(.title.bold())
                Spacer()
                Button("Done") { showDetails = false }
            }
            Text("Native AVKit playback for iPhone, iPad, Apple TV, and Apple Vision Pro, with HDR asset inspection, system captions and audio selection, Picture in Picture, and resume-state support in the shared core.")
                .foregroundStyle(.secondary)
            Text(AppleCapabilityProbe.current().platform)
                .font(.caption.bold())
                .foregroundStyle(.cyan)
        }
        .padding(30)
        .frame(maxWidth: 620)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24))
        .padding(30)
    }

    @MainActor
    private func playBundledDemo() {
        guard let bundledURL = Bundle.main.url(forResource: "openstream_demo", withExtension: "mp4") else { return }
        Task {
            let url = await offlineStore.record(for: "openstream-demo")?.localURL ?? bundledURL
            startPlayback(url: url)
        }
    }

    @MainActor
    private func startPlayback(url: URL) {
        let nextPlayer = AVPlayer(url: url)
        player = nextPlayer
        nextPlayer.play()

        Task {
            let inspection = try? await AppleAssetInspector.inspect(url: url)
            let format = inspection?.formats.first
            playbackLabels = format.map { value in
                switch value.dynamicRange {
                case .dolbyVision: ["DOLBY VISION"]
                case .hdr10Plus: ["HDR10+"]
                case .hdr10: ["HDR10"]
                case .hlg: ["HLG HDR"]
                case .sdr: []
                }
            } ?? []
            guard !playbackLabels.isEmpty else { return }
            showPlaybackLabels = true
            try? await Task.sleep(for: .seconds(3.5))
            showPlaybackLabels = false
        }
    }

    @MainActor
    private func toggleOfflineDemo() {
        guard !offlineBusy else { return }
        guard let url = Bundle.main.url(forResource: "openstream_demo", withExtension: "mp4") else { return }
        offlineBusy = true
        Task {
            if offlineAvailable {
                try? await offlineStore.remove(mediaID: "openstream-demo")
                offlineAvailable = false
            } else if (try? await offlineStore.makeAvailable(mediaID: "openstream-demo", sourceURL: url)) != nil {
                offlineAvailable = true
            }
            offlineBusy = false
        }
    }
}

private struct AppleOpenStreamLaunchView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var appeared = false

    var body: some View {
        ZStack {
            Color(red: 0.02, green: 0.025, blue: 0.035).ignoresSafeArea()
            Text("openStream")
                .font(.system(size: 38, weight: .heavy, design: .rounded))
                .tracking(1.4)
                .foregroundStyle(.white)
                .opacity(reduceMotion ? 1 : (appeared ? 1 : 0))
                .scaleEffect(reduceMotion ? 1 : (appeared ? 1 : 0.94))
        }
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeOut(duration: 0.52)) { appeared = true }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("OpenStream")
    }
}

@MainActor
private final class AppleNetworkStatus: ObservableObject {
    @Published private(set) var online = true
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.cyberbanksy.openstream.network")

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in self?.online = path.status == .satisfied }
        }
        monitor.start(queue: queue)
    }

    deinit { monitor.cancel() }
}
