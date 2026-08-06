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

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public func progress(for mediaID: String) -> ApplePlaybackProgress? {
        load()[mediaID]
    }

    public func save(mediaID: String, position: Double, duration: Double) {
        guard !mediaID.isEmpty, position.isFinite, duration.isFinite, duration > 0 else { return }
        var entries = load()
        entries[mediaID] = ApplePlaybackProgress(position: position, duration: duration)
        if position >= duration * 0.95 { entries.removeValue(forKey: mediaID) }
        persist(entries)
    }

    public func clear(mediaID: String) {
        var entries = load()
        entries.removeValue(forKey: mediaID)
        persist(entries)
    }

    private func load() -> [String: ApplePlaybackProgress] {
        guard let data = defaults.data(forKey: key) else { return [:] }
        return (try? JSONDecoder().decode([String: ApplePlaybackProgress].self, from: data)) ?? [:]
    }

    private func persist(_ entries: [String: ApplePlaybackProgress]) {
        defaults.set(try? JSONEncoder().encode(entries), forKey: key)
    }
}
