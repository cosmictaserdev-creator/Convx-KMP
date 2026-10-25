# Apple Music Web layout reference

This is a signed-out visual inventory of the public Apple Music Web layouts reviewed for the KMP clients. It records page templates, not every album, playlist, artist, or station instance. Personal library and account pages require sign-in and were not available in the public session.

## Shared application frame

- A narrow left navigation rail stays fixed while page content scrolls. It groups Search, Home, New, and Radio, with account/subscription actions below.
- The main area changes its content hierarchy by page. Artwork and section headers align to a consistent content gutter; wide desktop layouts use shelves and tables rather than stretching phone-sized lists.
- A persistent mini-player sits along the bottom edge with current artwork and title, transport controls, progress, and queue/output actions.
- Subscription prompts are separate from the content canvas and may appear along the lower edge in signed-out views.
- Pages favor artwork-led sections, quiet neutral surfaces, strong type hierarchy, and compact separators. Detail screens use denser metadata and track rows than discovery screens.

## Reviewed page templates

| Page | Main structure | Distinctive content and actions |
| --- | --- | --- |
| Home, signed-out landing | Large editorial hero followed by grouped promotion and content sections | Subscription entry and featured media; this public landing is not a personalized listening feed. |
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

## Interaction and implementation notes

- Keep the navigation rail and mini-player stable while changing the central page. Detail navigation should preserve playback state.
- Use a horizontal shelf for curated collections and a table/list for chart, album, playlist, and editorial track sequences.
- Search should have distinct empty-query and populated-result states; the category grid is not the results page.
- Do not treat the signed-out landing as equivalent to a personalized Home/Listen Now page. Authenticated library and recommendation layouts still need a signed-in reference session.
- These observations describe hierarchy and visible controls, not exact pixel measurements or behavior behind account gates.
