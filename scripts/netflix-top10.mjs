import { createNetflixTop10Service } from "../server/netflix-top10.mjs";

const emulate = process.argv.includes("--emulate");
const countryArgument = process.argv.find((argument) => argument.startsWith("--country="));
const country = countryArgument?.slice("--country=".length) || process.env.OPENSTREAM_NETFLIX_TOP10_COUNTRY || "US";

function emulatedTrendingPage() {
  const titles = [
    "Example One", "Example Two", "Example Three", "Example Four", "Example Five",
    "Example Six", "Example Seven", "Example Eight", "Example Nine", "Example Ten",
  ];
  return `<section data-uia="trending-now">${titles.map((title, index) =>
    `<a data-uia="titleId-${80_000_001 + index}" aria-label="Go to ${title}" href="https://www.netflix.com/title/${80_000_001 + index}"></a>`
  ).join("")}</section>`;
}

const service = createNetflixTop10Service({
  country,
  cacheFile: emulate ? null : undefined,
  fetchImpl: emulate
    ? async () => new Response(emulatedTrendingPage(), {
        status: 200,
        headers: { "content-type": "text/html" },
      })
    : globalThis.fetch,
  onError: (error) => console.error(error.message),
});

try {
  const snapshot = await service.start();
  console.log(JSON.stringify(snapshot, null, 2));
  if (!snapshot.lists.overall.length && !snapshot.lists.movies.length && !snapshot.lists.series.length) {
    process.exitCode = 1;
  }
} finally {
  service.close();
}
