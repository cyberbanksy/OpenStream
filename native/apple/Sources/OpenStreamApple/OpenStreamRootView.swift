import SwiftUI

/// Empty production shell. Media appears only after the viewer configures a source.
public struct OpenStreamRootView: View {
    @State private var selectedSection = "Discover"

    public init() {}

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 0) {
                topBar
                Spacer()
                emptyState
                Spacer()
            }
            .padding(.horizontal, 36)
            .padding(.vertical, 24)
        }
        .preferredColorScheme(.dark)
    }

    private var topBar: some View {
        HStack(spacing: 18) {
            OpenStreamThreeBarMark()
                .frame(width: 38, height: 28)
            Text("OpenStream")
                .font(.system(size: 27, weight: .bold))
            Spacer()
            ForEach(["Discover", "Library", "Live", "Sources", "Settings"], id: \.self) { section in
                Button(section) { selectedSection = section }
                    .buttonStyle(.plain)
                    .font(.system(size: 16, weight: selectedSection == section ? .bold : .medium))
                    .foregroundStyle(selectedSection == section ? Color.white : Color.white.opacity(0.62))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 12)
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 18) {
            OpenStreamThreeBarMark()
                .frame(width: 84, height: 60)
            Text(selectedSection == "Discover" ? "Nothing to show yet" : selectedSection)
                .font(.system(size: 34, weight: .bold))
            Text("Add libraries, add-ons, YouTube channels, and Live TV from Sources. OpenStream never ships requested content as a built-in catalog.")
                .font(.system(size: 17))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 620)
            Button("Open Sources") { selectedSection = "Sources" }
                .buttonStyle(.borderedProminent)
        }
        .accessibilityElement(children: .contain)
    }
}

public struct OpenStreamThreeBarMark: View {
    public init() {}

    public var body: some View {
        GeometryReader { geometry in
            let stroke = max(3, geometry.size.height * 0.18)
            VStack(spacing: geometry.size.height * 0.12) {
                bar(width: 0.72, stroke: stroke)
                bar(width: 1, stroke: stroke)
                bar(width: 0.72, stroke: stroke)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .accessibilityHidden(true)
    }

    private func bar(width: CGFloat, stroke: CGFloat) -> some View {
        Capsule()
            .fill(Color.white)
            .frame(maxWidth: .infinity)
            .frame(height: stroke)
            .scaleEffect(x: width, y: 1)
    }
}
