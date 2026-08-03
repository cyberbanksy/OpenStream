import SwiftUI

public struct OpenStreamRootView: View {
    private enum FocusTarget: Hashable {
        case discover
        case library
        case live
        case connections
        case play
        case details
        case media(Int)
    }

    @FocusState private var focus: FocusTarget?
    @State private var selectedSection = "Discover"

    public init() {}

    public var body: some View {
        ZStack {
            Image("northstar-hero", bundle: .module)
                .resizable()
                .scaledToFill()
                .ignoresSafeArea()

            LinearGradient(
                stops: [
                    .init(color: Color.black.opacity(0.98), location: 0),
                    .init(color: Color.black.opacity(0.78), location: 0.28),
                    .init(color: Color.black.opacity(0.08), location: 0.72),
                ],
                startPoint: .leading,
                endPoint: .trailing
            )
            .ignoresSafeArea()

            LinearGradient(
                colors: [.clear, Color.black.opacity(0.94)],
                startPoint: .center,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                topBar
                hero
                Spacer(minLength: 20)
                continueWatching
            }
            .padding(.horizontal, 64)
            .padding(.top, 22)
            .padding(.bottom, 36)
        }
        .background(Color.black)
        .tint(.cyan)
        .preferredColorScheme(.dark)
        .onAppear { focus = .play }
    }

    private var topBar: some View {
        HStack(spacing: 28) {
            HStack(spacing: 12) {
                Image("openstream-mark", bundle: .module)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 38, height: 38)
                    .clipShape(Circle())
                Text("OpenStream")
                    .font(.system(size: 28, weight: .bold))
                Text("by Orgista")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.secondary)
            }
            .accessibilityElement(children: .combine)

            Spacer()

            HStack(spacing: 14) {
                navigationButton("Discover", target: .discover)
                navigationButton("Library", target: .library)
                navigationButton("Live", target: .live)
                navigationButton("Connections", target: .connections)
            }

            Spacer()

            Button(action: {}) {
                Image(systemName: "magnifyingglass")
                    .frame(width: 42, height: 42)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Search")
        }
        .frame(height: 64)
    }

    private func navigationButton(_ title: String, target: FocusTarget) -> some View {
        Button {
            selectedSection = title
        } label: {
            Text(title)
                .font(.system(size: 17, weight: selectedSection == title ? .bold : .semibold))
                .foregroundStyle(selectedSection == title ? Color.white : Color.white.opacity(0.68))
                .frame(minWidth: 106, minHeight: 44)
                .overlay(alignment: .bottom) {
                    Rectangle()
                        .fill(selectedSection == title ? Color.cyan : Color.clear)
                        .frame(height: 3)
                }
        }
        .buttonStyle(.plain)
        .focused($focus, equals: target)
    }

    private var hero: some View {
        HStack {
            VStack(alignment: .leading, spacing: 18) {
                Spacer(minLength: 30)

                Text("NORTHSTAR")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.cyan)

                Text("Northstar")
                    .font(.system(size: 64, weight: .heavy))

                HStack(spacing: 10) {
                    Text("2025")
                    Text("PG-13")
                    Text("2h 07m")
                    formatLabel("4K")
                    formatLabel("HDR10")
                    formatLabel("ATMOS")
                }
                .font(.system(size: 15, weight: .semibold))

                Text("A stranded pilot leads a rescue mission across a frozen frontier to save a lost expedition and confront what was never meant to be found.")
                    .font(.system(size: 19, weight: .regular))
                    .foregroundStyle(Color.white.opacity(0.84))
                    .lineSpacing(5)
                    .frame(maxWidth: 620, alignment: .leading)

                HStack(spacing: 14) {
                    primaryAction
                    secondaryAction
                }
                .padding(.top, 2)
            }
            .frame(maxWidth: 700, alignment: .leading)

            Spacer()
        }
        .frame(maxHeight: 470)
    }

    @ViewBuilder
    private var primaryAction: some View {
        let button = Button(action: {}) {
            Label("Stream", systemImage: "play.fill")
                .font(.system(size: 18, weight: .bold))
                .frame(minWidth: 160, minHeight: 54)
        }
        .focused($focus, equals: .play)

        #if os(visionOS)
        button.buttonStyle(.borderedProminent)
        #else
        if #available(macOS 26.0, tvOS 26.0, *) {
            button.buttonStyle(.glassProminent)
        } else {
            button.buttonStyle(.borderedProminent)
        }
        #endif
    }

    @ViewBuilder
    private var secondaryAction: some View {
        let button = Button(action: {}) {
            Label("Details", systemImage: "info.circle")
                .font(.system(size: 18, weight: .bold))
                .frame(minWidth: 160, minHeight: 54)
        }
        .focused($focus, equals: .details)

        #if os(visionOS)
        button.buttonStyle(.bordered)
        #else
        if #available(macOS 26.0, tvOS 26.0, *) {
            button.buttonStyle(.glass)
        } else {
            button.buttonStyle(.bordered)
        }
        #endif
    }

    private func formatLabel(_ value: String) -> some View {
        Text(value)
            .font(.system(size: 11, weight: .bold))
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .overlay {
                RoundedRectangle(cornerRadius: 4)
                    .stroke(Color.white.opacity(0.42), lineWidth: 1)
            }
    }

    private var continueWatching: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Continue Watching")
                .font(.system(size: 22, weight: .bold))

            ScrollView(.horizontal) {
                LazyHStack(spacing: 16) {
                    ForEach(Array(media.enumerated()), id: \.offset) { index, item in
                        Button(action: {}) {
                            NativeMediaTile(item: item)
                        }
                        .buttonStyle(.plain)
                        .focused($focus, equals: .media(index))
                        .accessibilityLabel("Open \(item.title)")
                    }
                }
            }
            .scrollIndicators(.hidden)
        }
        .frame(height: 236)
    }

    private var media: [NativeMediaItem] {
        [
            .init(title: "The Last Signal", subtitle: "45m left", cell: (1, 0)),
            .init(title: "Solar Drift", subtitle: "1h 34m left", cell: (2, 0)),
            .init(title: "Echoes of Mars", subtitle: "S1 E4", cell: (3, 0)),
            .init(title: "Frontier Outpost", subtitle: "S2 E6", cell: (0, 1)),
            .init(title: "Silent Tides", subtitle: "4K HDR10", cell: (1, 2)),
        ]
    }
}

private struct NativeMediaItem {
    let title: String
    let subtitle: String
    let cell: (Int, Int)
}

private struct NativeMediaTile: View {
    let item: NativeMediaItem

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            GeometryReader { proxy in
                Image("media-sheet", bundle: .module)
                    .resizable()
                    .frame(width: proxy.size.width * 4, height: proxy.size.height * 3)
                    .offset(x: -proxy.size.width * CGFloat(item.cell.0), y: -proxy.size.height * CGFloat(item.cell.1))
            }

            LinearGradient(
                colors: [.clear, Color.black.opacity(0.92)],
                startPoint: .center,
                endPoint: .bottom
            )

            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .font(.system(size: 16, weight: .bold))
                    .lineLimit(1)
                Text(item.subtitle)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.white.opacity(0.72))
            }
            .padding(14)
        }
        .frame(width: 278, height: 174)
        .clipShape(RoundedRectangle(cornerRadius: 7))
        .contentShape(RoundedRectangle(cornerRadius: 7))
    }
}
