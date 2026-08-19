import { fileURLToPath } from "node:url";

const BASE_URL = "https://v3-cinemeta.strem.io";

export const TITLE_SEARCH_CASES = [
  ["movie", "The Shawshank Redemption"],
  ["movie", "The Godfather"],
  ["movie", "The Dark Knight"],
  ["movie", "Pulp Fiction"],
  ["movie", "The Lord of the Rings: The Return of the King"],
  ["movie", "Forrest Gump"],
  ["movie", "Inception"],
  ["movie", "Fight Club"],
  ["movie", "The Matrix"],
  ["movie", "Goodfellas"],
  ["movie", "Interstellar"],
  ["movie", "Se7en", "Seven"],
  ["movie", "The Silence of the Lambs"],
  ["movie", "Saving Private Ryan"],
  ["movie", "Spirited Away"],
  ["movie", "The Green Mile"],
  ["movie", "Parasite"],
  ["movie", "Gladiator"],
  ["movie", "Whiplash"],
  ["movie", "The Prestige"],
  ["movie", "The Departed"],
  ["movie", "The Lion King"],
  ["movie", "Back to the Future"],
  ["movie", "Alien"],
  ["movie", "Apocalypse Now"],
  ["movie", "Memento"],
  ["movie", "Raiders of the Lost Ark"],
  ["movie", "WALL-E"],
  ["movie", "The Lives of Others"],
  ["movie", "Sunset Boulevard"],
  ["movie", "Dr. Strangelove or: How I Learned to Stop Worrying and Love the Bomb"],
  ["movie", "Avengers: Infinity War"],
  ["movie", "Spider-Man: No Way Home"],
  ["movie", "Spider-Man: Into the Spider-Verse"],
  ["movie", "Dune: Part Two"],
  ["movie", "Oppenheimer"],
  ["movie", "Everything Everywhere All at Once"],
  ["movie", "Top Gun: Maverick"],
  ["movie", "Mad Max: Fury Road"],
  ["movie", "The Grand Budapest Hotel"],
  ["movie", "Get Out"],
  ["movie", "Knives Out"],
  ["movie", "The Social Network"],
  ["movie", "No Country for Old Men"],
  ["movie", "There Will Be Blood"],
  ["movie", "Eternal Sunshine of the Spotless Mind"],
  ["movie", "The Truman Show"],
  ["movie", "Jurassic Park"],
  ["movie", "Terminator 2: Judgment Day"],
  ["movie", "The Good, the Bad and the Ugly"],
  ["series", "Band of Brothers"],
  ["series", "Breaking Bad"],
  ["series", "The Sopranos"],
  ["series", "The Wire"],
  ["series", "Game of Thrones"],
  ["series", "Chernobyl"],
  ["series", "Better Call Saul"],
  ["series", "Stranger Things"],
  ["series", "The Last of Us"],
  ["series", "Succession"],
  ["series", "The Office"],
  ["series", "Parks and Recreation"],
  ["series", "Seinfeld"],
  ["series", "Friends"],
  ["series", "The Simpsons"],
  ["series", "Futurama"],
  ["series", "Rick and Morty"],
  ["series", "Avatar: The Last Airbender"],
  ["series", "Fullmetal Alchemist: Brotherhood"],
  ["series", "Attack on Titan"],
  ["series", "Arcane"],
  ["series", "Bluey"],
  ["series", "The Bear"],
  ["series", "Ted Lasso"],
  ["series", "The Mandalorian"],
  ["series", "Andor"],
  ["series", "House of the Dragon"],
  ["series", "The Boys"],
  ["series", "Invincible"],
  ["series", "Black Mirror"],
  ["series", "True Detective"],
  ["series", "Fargo"],
  ["series", "Mindhunter"],
  ["series", "Sherlock"],
  ["series", "Peaky Blinders"],
  ["series", "Narcos"],
  ["series", "Mr. Robot"],
  ["series", "Dark"],
  ["series", "Lost"],
  ["series", "Twin Peaks"],
  ["series", "The X-Files"],
  ["series", "Star Trek: The Next Generation"],
  ["series", "Doctor Who"],
  ["series", "It's Always Sunny in Philadelphia"],
  ["series", "Brooklyn Nine-Nine"],
  ["series", "Community"],
  ["series", "Arrested Development"],
  ["series", "The Crown"],
  ["series", "Yellowstone"],
  ["series", "Only Murders in the Building"],
].map(([type, query, expectedTitle = query]) => ({ type, query, expectedTitle }));

export function normalizeTitle(value) {
  return value.normalize("NFD").replace(/\p{M}+/gu, "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
}

export function searchUrl({ type, query }, baseUrl = BASE_URL) {
  return `${baseUrl}/catalog/${encodeURIComponent(type)}/top/search=${encodeURIComponent(query)}.json`;
}

export function exactTitle(metas, query) {
  const expected = normalizeTitle(query);
  return metas.find((meta) => normalizeTitle(meta?.name ?? meta?.title ?? "") === expected) ?? null;
}

async function fetchSearch(searchCase, fetchImpl) {
  let error = "request failed";
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      const response = await fetchImpl(searchUrl(searchCase), {
        headers: { accept: "application/json", "user-agent": "OpenStream-search-audit/1" },
        signal: AbortSignal.timeout(15_000),
      });
      if (response.ok) {
        const payload = await response.json();
        const match = exactTitle(Array.isArray(payload?.metas) ? payload.metas : [], searchCase.expectedTitle);
        return match
          ? { type: searchCase.type, query: searchCase.query, ok: true, id: match.id }
          : { type: searchCase.type, query: searchCase.query, ok: false, error: "no exact title result" };
      }
      error = `HTTP ${response.status}`;
      if (response.status !== 429 && response.status < 500) break;
    } catch (failure) {
      error = failure instanceof Error ? failure.message : "network failure";
    }
    await new Promise((resolve) => setTimeout(resolve, 250 * (attempt + 1)));
  }
  return { type: searchCase.type, query: searchCase.query, ok: false, error };
}

export async function auditTitleSearch({ cases = TITLE_SEARCH_CASES, concurrency = 6, fetchImpl = fetch } = {}) {
  const results = Array(cases.length);
  let cursor = 0;
  const worker = async () => {
    while (cursor < cases.length) {
      const index = cursor;
      cursor += 1;
      results[index] = await fetchSearch(cases[index], fetchImpl);
    }
  };
  await Promise.all(Array.from({ length: Math.min(concurrency, cases.length) }, worker));
  return results;
}

async function main() {
  const startedAt = Date.now();
  const results = await auditTitleSearch();
  const failures = results.filter((result) => !result.ok);
  console.log(JSON.stringify({
    tested: results.length,
    passed: results.length - failures.length,
    failed: failures.length,
    durationMs: Date.now() - startedAt,
    failures,
  }, null, 2));
  if (failures.length > 0) process.exitCode = 1;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) await main();
