import OpenStreamApple
import SwiftUI

@main
struct OpenStreamPlatformApp: App {
    @State private var showLaunch = true
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some Scene {
        WindowGroup {
            ZStack {
                OpenStreamRootView()

                if showLaunch {
                    AppleOpenStreamLaunchView()
                        .transition(.opacity)
                        .zIndex(20)
                }
            }
            .task {
                try? await Task.sleep(for: .milliseconds(reduceMotion ? 220 : 1_300))
                withAnimation(.easeOut(duration: 0.22)) { showLaunch = false }
            }
        }
    }
}

private struct AppleOpenStreamLaunchView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var appeared = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 18) {
                OpenStreamThreeBarMark()
                    .frame(width: 74, height: 52)
                Text("openStream")
                    .font(.system(size: 38, weight: .heavy, design: .rounded))
                    .tracking(1.4)
                    .foregroundStyle(.white)
            }
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
