import Foundation

public struct ApplePlaybackProgress: Codable, Equatable, Sendable {
    public let position: Double
    public let duration: Double
    public let updatedAt: Date

    public init(position: Double, duration: Double, updatedAt: Date = .now) {
        self.position = position
        self.duration = duration
        self.updatedAt = updatedAt
    }

    public var resumePosition: Double? {
        guard duration > 0, position >= 15, position < duration * 0.95 else { return nil }
        return position
    }
}

@MainActor
public final class ApplePlaybackStore {
    private let defaults: UserDefaults
    private let key = "openstream.playback.progress.v1"

    /// In-memory copy of the progress map. UserDefaults stays the backing
    /// store (write-through), but reads and each per-tick save no longer
    /// decode + re-encode the whole dictionary. `nil` means not yet loaded.
    private var cache: [String: ApplePlaybackProgress]?

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public func progress(for mediaID: String) -> ApplePlaybackProgress? {
        entries()[mediaID]
    }

    public func save(mediaID: String, position: Double, duration: Double) {
        guard !mediaID.isEmpty, position.isFinite, duration.isFinite, duration > 0 else { return }
        var current = entries()
        current[mediaID] = ApplePlaybackProgress(position: position, duration: duration)
        if position >= duration * 0.95 { current.removeValue(forKey: mediaID) }
        persist(current)
    }

    public func clear(mediaID: String) {
        var current = entries()
        current.removeValue(forKey: mediaID)
        persist(current)
    }

    private func entries() -> [String: ApplePlaybackProgress] {
        if let cache { return cache }
        let loaded: [String: ApplePlaybackProgress]
        if let data = defaults.data(forKey: key) {
            loaded = (try? JSONDecoder().decode([String: ApplePlaybackProgress].self, from: data)) ?? [:]
        } else {
            loaded = [:]
        }
        cache = loaded
        return loaded
    }

    private func persist(_ entries: [String: ApplePlaybackProgress]) {
        cache = entries
        defaults.set(try? JSONEncoder().encode(entries), forKey: key)
    }
}
