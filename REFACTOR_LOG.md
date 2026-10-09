# N-Crawler refactor log

Baseline taken from `main_zip__tar.gz` (app/src/main) on 2026-10-07.

**Baseline: 70 Kotlin files, 21,026 lines** (+150 lines of XML/resources, untouched).

Line makeup at baseline: ~1,720 blank, ~3,420 comment (16%), ~1,370 import lines. 7 files are over 700 lines; the top 3 are 7,009 lines = 33% of the app.

## How we keep score

- Run `./refactor_loc.sh` after every phase; paste the new totals into the Progress log below.
- Rule: one phase per push, build must pass and the app must run before the next phase starts.
- Safety net before phase 1: `git tag pre-refactor` (restore any time with `git checkout pre-refactor`).

## Findings from the audit

1. **Dead scraper**: `NovelArrowSource.kt` (450 lines) is not in `SourceRegistry`. Nothing calls it.
2. **Unused icons**: ~240 lines in `SolarIcons.kt` / `NavIcons.kt` (about 40 icons that nothing references).
3. **Unused functions/classes**: `GenreChip`, `rewrapChapters`, `searchBySource`, `availableSources`, `pruneOldCache`, `setConcurrentLimit`, `showMessage`, `downloadFirst`, `downloadMissing`, `isEnabled`, `getLast`, `OnImageGlass*` colours, `BORDER`, `GlassSurfaceMuted`, `NavBgColor` (verify each before deleting).
4. **Copy-pasted top-bar morph**: `mix`, `window`, `collapseOf`, `MorphGeometry`, `MorphBarShape` exist in both BrowseScreen and SourceSettingsScreen (34 duplicate blocks).
5. **Other duplicates**: `SectionHeader` (Browse + Downloads), glass alpha constants (Browse + Reader), `htmlToPlainText` x3, `slugFromUrl` x3, `coverUrlFor`/`ratingOutOfTen`/`statusFromCode` x2, `extractCoverUrl`/`findCoverNear` x2.
6. **Scrapers**: FreeWebNovel and NovelLive share one site template (~90 duplicate blocks); every scraper repeats its own OkHttp/Jsoup/user-agent plumbing.
7. **Giant single functions**: `ReaderScreen()` ~775 lines, `DetailScreen()` + `CinematicDetail()` ~730, `BrowseContent()` ~280.
8. **History comments**: 84+ `CHANGE`/`FIX` comments in the big files describe past edits; git already stores that.

## Plan (each phase = one push)

| Phase | Change | Advantage | Disadvantage | Est. lines saved |
|---|---|---|---|---|
| 1 Dead code | Delete NovelArrowSource, unused icons, unused functions/constants | Zero behaviour change, smaller APK, faster builds | Loses the NovelArrow reverse-engineering notes (still in git history/tag); a symbol could be used via reflection (unlikely here) | ~700-850 |
| 2 Shared UI helpers | New `ui/components/TopBarMorph.kt` (mix, window, collapseOf, MorphGeometry, MorphBarShape), shared SectionHeader and glass constants; Browse + Settings import them | One place to tweak the pill morph; Browse and Settings can't drift apart | Touches the screens you just polished, so a visual regression is possible; needs careful before/after check | ~150-250 |
| 3 Scrapers | New `ScraperUtils.kt` (htmlToPlainText, slugFromUrl, cover/rating/status helpers, shared OkHttp call); NovelLive reuses FreeWebNovel's template via a small base class | Adding a new site gets much cheaper; fixes apply to both same-template sites | Base class couples two sites: if one changes its HTML, the other must be split back out | ~250-400 |
| 4 Split giant screens | Move parts into new files in the same package: Reader (Fx, Sheets, TOC, NavBar, Header), Detail (Skeleton, DownloadSheet, Banner, ChapterRow), Browse (TopChrome, SearchOverlay, Skeleton, Hero). Break the 775-line `ReaderScreen()` into smaller composables | Every file < ~700 lines, faster incremental compile on your low-end PC, easier to find things | Net lines go UP slightly (imports + `private` becomes `internal`); biggest risk of a missed import/visibility error, so we do one screen per push | -0 to +100 |
| 5 NovelRepository split | Separate download and search logic out of the 826-line repository (e.g. `DownloadRepository`, `SearchRepository`) | Smaller classes, workers/viewmodels depend only on what they need | Constructor/wiring changes in NCrawlerApp and ViewModels | ~0 (restructure) |
| 6 Comments (optional, your call) | Remove `CHANGE`/`FIX` history comments and redundant imports, keep the "why" comments | ~500-700 fewer lines, cleaner files | You lose in-file notes of past decisions; I'd keep anything explaining a non-obvious reason | ~500-700 |

**Realistic target:** about 21,000 -> ~19,000 lines (phases 1-3 and 6), with the three giants each cut to ~1,000-1,600 lines or less per file after phase 4.

## Baseline line counts per file

| File | Baseline | Current | Change | Notes |
|---|---:|---:|---:|---|
| ui/screens/reader/ReaderScreen.kt | 2,785 | 2,785 | 0 | ReaderScreen() alone is ~775 lines; FX, sheets, TOC, nav bar all private in one file |
| ui/screens/detail/DetailScreen.kt | 2,156 | 2,156 | 0 | DetailScreen()+CinematicDetail ~730 lines; skeleton, download sheet, banner, bubble all inline |
| ui/screens/browse/BrowseScreen.kt | 2,068 | 2,068 | 0 | TopChrome morph, SearchOverlay, hero, skeletons in one file; GenreChip + OnImageGlass* unused |
| data/repository/NovelRepository.kt | 826 | 826 | 0 | search, downloads, library, updates in one class; rewrapChapters/searchBySource/availableSources unused |
| ui/screens/downloads/DownloadsScreen.kt | 764 | 764 | 0 | own SectionHeader duplicate |
| ui/NavGraph.kt | 745 | 745 | 0 |  |
| ui/screens/settings/SourceSettingsScreen.kt | 742 | 742 | 0 | mix/window/collapseOf/MorphGeometry/MorphBarShape copied from BrowseScreen |
| ui/components/SolarIcons.kt | 586 | 586 | 0 | ~223 lines are icons nothing references |
| ui/screens/reader/ReaderAutoScroll.kt | 553 | 553 | 0 |  |
| viewmodel/ReaderViewModel.kt | 542 | 542 | 0 |  |
| data/worker/ChapterDownloadWorker.kt | 541 | 541 | 0 |  |
| data/scraper/NovelPingSource.kt | 489 | 489 | 0 | copies htmlToPlainText/coverUrlFor/ratingOutOfTen/statusFromCode from NovelArrowSource |
| data/worker/EpubExportWorker.kt | 468 | 468 | 0 |  |
| ui/components/LottieLite.kt | 451 | 451 | 0 |  |
| data/scraper/NovelArrowSource.kt | 450 | 450 | 0 | NOT registered in SourceRegistry (dead); NovelPingSource is its successor |
| ui/screens/library/LibraryScreen.kt | 436 | 436 | 0 |  |
| data/scraper/FreeWebNovelScraper.kt | 386 | 386 | 0 | ~90 duplicate 8-line blocks shared with NovelLiveSource |
| data/scraper/NovelFullSource.kt | 379 | 379 | 0 |  |
| viewmodel/DetailViewModel.kt | 348 | 348 | 0 | showMessage/downloadFirst/downloadMissing unused |
| data/scraper/NovelLiveSource.kt | 345 | 345 | 0 | same template as FreeWebNovel; mostly a copy |
| ui/components/GlassCards.kt | 320 | 320 | 0 |  |
| data/scraper/LightNovelWorldSource.kt | 301 | 301 | 0 |  |
| data/scraper/NovelBuddySource.kt | 284 | 284 | 0 |  |
| viewmodel/BrowseViewModel.kt | 267 | 267 | 0 |  |
| ui/components/Motion.kt | 256 | 256 | 0 |  |
| ui/screens/discover/GenreScreen.kt | 246 | 246 | 0 |  |
| viewmodel/DownloadsViewModel.kt | 220 | 220 | 0 |  |
| data/local/DownloadPreferences.kt | 215 | 215 | 0 | setConcurrentLimit unused |
| ui/theme/Theme.kt | 205 | 205 | 0 |  |
| ui/components/PullToRefresh.kt | 198 | 198 | 0 |  |
| viewmodel/GenreViewModel.kt | 139 | 139 | 0 |  |
| data/local/ForegroundBudget.kt | 118 | 118 | 0 |  |
| ui/components/AnimatedIcons.kt | 117 | 117 | 0 |  |
| data/worker/UpdateCheckWorker.kt | 109 | 109 | 0 |  |
| NCrawlerApp.kt | 108 | 108 | 0 |  |
| ui/components/NavIcons.kt | 98 | 98 | 0 | ~17 lines unused icons |
| viewmodel/SourceSettingsViewModel.kt | 95 | 95 | 0 |  |
| data/scraper/SourcePreferences.kt | 91 | 91 | 0 | isEnabled unused |
| data/db/NovelDao.kt | 89 | 89 | 0 | pruneOldCache unused |
| ui/screens/discover/DiscoverScreen.kt | 87 | 87 | 0 |  |
| data/scraper/NovelSource.kt | 84 | 84 | 0 |  |
| data/db/AppDatabase.kt | 84 | 84 | 0 |  |
| viewmodel/LibraryViewModel.kt | 83 | 83 | 0 |  |
| data/db/ChapterDao.kt | 80 | 80 | 0 |  |
| data/scraper/SourceRegistry.kt | 74 | 74 | 0 |  |
| ui/components/RefreshIcon.kt | 71 | 71 | 0 |  |
| ui/components/GlassModeCard.kt | 70 | 70 | 0 |  |
| ui/components/SlidersIcon.kt | 56 | 56 | 0 |  |
| data/local/ReadingPositionStore.kt | 56 | 56 | 0 |  |
| data/repository/ChapterFetchGuard.kt | 55 | 55 | 0 |  |
| data/local/ReaderPrefsStore.kt | 55 | 55 | 0 |  |
| ui/components/SolarStars.kt | 53 | 53 | 0 |  |
| data/db/ProgressDao.kt | 53 | 53 | 0 |  |
| MainActivity.kt | 53 | 53 | 0 |  |
| data/db/ReaderBookmark.kt | 48 | 48 | 0 |  |
| data/db/NovelEntity.kt | 45 | 45 | 0 |  |
| data/local/RecentSearchStore.kt | 44 | 44 | 0 |  |
| ui/theme/Fonts.kt | 40 | 40 | 0 |  |
| ui/components/SolarArrows.kt | 40 | 40 | 0 |  |
| ui/theme/GlassMode.kt | 38 | 38 | 0 |  |
| data/local/ReadChaptersStore.kt | 37 | 37 | 0 |  |
| data/local/DominantColorStore.kt | 29 | 29 | 0 | getLast unused |
| data/db/ChapterEntity.kt | 28 | 28 | 0 |  |
| data/local/UpdateCheckStore.kt | 27 | 27 | 0 |  |
| data/db/DownloadProgress.kt | 25 | 25 | 0 |  |
| data/repository/SearchSection.kt | 22 | 22 | 0 |  |
| viewmodel/ErrorText.kt | 20 | 20 | 0 |  |
| data/db/ReadingProgress.kt | 17 | 17 | 0 |  |
| ui/components/FavouriteStyle.kt | 9 | 9 | 0 |  |
| data/scraper/ChapterLink.kt | 7 | 7 | 0 |  |
| **TOTAL** | **21,026** | **21,026** | **0** | |

## Progress log

| Date | Phase | What was removed/merged | Total lines before | Total lines after | Redundant code cleared |
|---|---|---|---:|---:|---|
| 2026-10-07 | Baseline | audit only, no code changed | 21,026 | 21,026 | none yet |
