# Apple Music Web layout reference

This is a signed-out page-template inventory of public Apple Music Web. It combines prior frame notes with this pass's live route/content inspection; this pass used text-rendered pages and did not capture screenshot pixels. It records distinct page templates and states, not every album, playlist, artist, or station instance. Personal library and account pages require sign-in and were not available in the public session.

## Shared application frame

- A narrow left navigation rail stays fixed while page content scrolls. It groups Search, Home, New, and Radio, with account/subscription actions below.
- The main area changes its content hierarchy by page. Artwork and section headers align to a consistent content gutter; wide desktop layouts use shelves and tables rather than stretching phone-sized lists.
- A persistent mini-player sits along the bottom edge with current artwork and title, transport controls, progress, and queue/output actions.
- Subscription prompts are separate from the content canvas and may appear along the lower edge in signed-out views.
- Pages favor artwork-led sections, quiet neutral surfaces, strong type hierarchy, and compact separators. Detail screens use denser metadata and track rows than discovery screens.

## Reviewed page templates

| Page | Main structure | Distinctive content and actions |
| --- | --- | --- |
| Home / signed-out access | The `/us/home` text capture did not expose a dependable Home body | Do not infer the personalized Home feed from this capture; signed-in Home remains unreviewed. |
| New | Editorial hero followed by horizontally browsable album and playlist shelves | New releases, featured picks, and section-level browse actions. |
| Radio | Live station artwork row, genre collections, and episode/program shelves | Station selection and curated radio entry points. |
| Search, empty query | Search field and browsable category grid | Category tiles provide a visual starting point before a query is entered. |
| Search results | Query header and result groups for songs, albums, artists, and playlists | Result rows/cards expose play and navigation affordances; group actions continue into fuller result lists. |
| Album detail | Large artwork and metadata header above a track table | Album title, artist/year details, primary play/shuffle actions, and ordered track rows. |
| Artist detail | Artist hero followed by sectioned discography and related content | Prominent play/follow controls, then albums, songs, and similar artists. |
| Playlist detail | Artwork and playlist metadata above a dense track table | Curator and description details, primary play action, and playlist tracks. |
| Top Charts | Ranked song list with a chart header and regional/city chart links | Numbered positions and compact track metadata carry more information than artwork cards. |
| Radio station | Station artwork and title above station-specific content | A direct play action and associated programming. |
| Editorial room | Editorial heading followed by a track table | Curator context and track-focused rows, with less emphasis on large card grids. |

## Expanded route audit (October 25, 2026)

I re-opened the signed-out web player and followed the public navigation and representative content links. This audit covers distinct page templates and states; Apple Music has too many individual catalogue items to inspect each album, artist, song, or playlist instance separately.

| Template / state | Representative route | What is present |
| --- | --- | --- |
| New / Browse | [`/us/new`](https://music.apple.com/us/new) | Editorial feature cards followed by shelves for new songs, weekly releases, albums, playlists, trending tracks, charts, and Apple Music exclusives. |
| Radio index | [`/us/radio`](https://music.apple.com/us/radio) | Live station entry points, radio episodes and interviews, then local and international broadcaster groups. |
| Search landing | [`/us/search`](https://music.apple.com/us/search) | Search input and a category grid spanning genres, charts, moods, music videos, DJ mixes, and editorial topics. |
| Search results | Search with a populated query | Kept as a separate state from the empty category grid. The query result route is dynamic, so each search term is data for the same result template. |
| Category / curator | [`Apple Music Pop`](https://music.apple.com/us/curator/apple-music-pop/976439548) | A category-specific editorial hero and a long mix of playlist shelves, new releases, songs, and exclusive features. |
| Playlist detail | [`A-List Pop`](https://music.apple.com/us/playlist/a-list-pop/pl.5ee8333dbe944d9f9151e97d92d1ead9) | Artwork, curator, update date, description, preview, song count and total duration, featured artists, and an ordered track sequence. |
| Artist detail | [`Megan Thee Stallion`](https://music.apple.com/us/artist/megan-thee-stallion/1258989914) | Latest release, top songs, albums, singles and EPs, music videos, and other artist-related shelves. Upcoming releases are another state of this template. |
| Album detail | [`Good News`](https://music.apple.com/us/album/good-news/1540031620) | Artwork and album metadata, editorial description, release/runtime information, versions, videos, featured-on links, and related albums. |
| Song selected from an album | [`WAP`](https://music.apple.com/us/album/wap-feat-megan-thee-stallion/1821526317?i=1821526567) | The public URL keeps the album route and adds a selected-song identifier. The text view resolves the album context; a distinct full-screen player state requires playback interaction. |
| Top Charts | [`Top Charts`](https://music.apple.com/us/new/top-charts) | Ranked song, album, playlist, and video sections, plus city chart collections. A chart-family link opens its own ranking route. |
| Editorial room | [`Subscribe to Play Episodes`](https://music.apple.com/us/room/6743553488) | A named editorial collection made from episode, interview, and story cards; it is distinct from an individual artist or playlist page. |
| Signed-out Home | [`/us/home`](https://music.apple.com/us/home) | The current text capture surfaced the country/region selector instead of the underlying personalized Home feed. Do not infer the signed-in feed from this result. |
| Personal library / account | Account-gated | Library and personalized listening states need an authenticated session and were not part of the signed-out route pass. |

The web text capture confirms the content and route families but does not expose a trustworthy screenshot of the live player's transient states. Queue, lyrics, device/output menus, expanded Now Playing, and playback overlays still need an interactive visual capture before treating their exact layout or motion as verified.

### Additional route variants opened

| Template / state | Representative route | Observed variation |
| --- | --- | --- |
| Top Songs chart | [`/us/new/top-charts/songs`](https://music.apple.com/us/new/top-charts/songs) | Ranked song rows with genre and region selectors. |
| Top Albums chart | [`/us/new/top-charts/albums`](https://music.apple.com/us/new/top-charts/albums) | Ranked album artwork and metadata, with genre and region selectors. |
| Top Playlists chart | [`/us/new/top-charts/playlists`](https://music.apple.com/us/new/top-charts/playlists) | Ranked playlist artwork and curator metadata. |
| Top Videos chart | [`/us/new/top-charts/music-videos`](https://music.apple.com/us/new/top-charts/music-videos) | Ranked video rows with video titles and artists. |
| Live station detail | [`Apple Music 1`](https://music.apple.com/us/station/apple-music-1/ra.978194965) | Station artwork/title, “Live Station” label, and direct Play action. |
| Music videos shelf | [`/us/new`](https://music.apple.com/us/new) and [`Apple Music Pop`](https://music.apple.com/us/curator/apple-music-pop/976439548) | New Music Videos is a distinct shelf on discovery and curator pages. A direct video detail URL was inaccessible to this text capture. |
| Search result state | Search with a populated query | A direct query URL was inaccessible to this text capture; result groups and interaction remain unverified here. |
| Selected song on album | [`WAP`](https://music.apple.com/us/album/wap-feat-megan-thee-stallion/1821526317?i=1821526567) | The selected-song link resolves to album context in the text capture; the Now Playing state was not opened. |

The public signed-out routes inspected cover the distinct discovery, chart, curator, catalog-detail, station, and editorial-room templates listed above. “Every page” here means each distinct public page template/state, not every catalog record or every personalized/account state. Search results, music-video detail, playback overlays, and signed-in library/Home remain gaps because this inspection had no interactive authenticated browser session.

## Interaction and implementation notes

- Keep the navigation rail and mini-player stable while changing the central page. Detail navigation should preserve playback state.
- Use a horizontal shelf for curated collections and a table/list for chart, album, playlist, and editorial track sequences.
- Search should have distinct empty-query and populated-result states; the category grid is not the results page.
- Do not treat the signed-out landing as equivalent to a personalized Home/Listen Now page. Authenticated library and recommendation layouts still need a signed-in reference session.
- These observations describe hierarchy and visible controls, not exact pixel measurements or behavior behind account gates.
