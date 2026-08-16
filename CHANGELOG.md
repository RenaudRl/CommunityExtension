# Changelog

## 0.10 — 2026-08-16

### Fact webhooks

- Added `webhook_fact_event`, a configurable fact-change event that publishes to any reusable
  `webhook_definition` destination.
- Supports player, Typewriter group and global scopes, optional Typewriter audiences, value
  conditions, message/embed templates, forum threads and role mentions.
- Group and global scopes deduplicate callbacks from players observing the same grouped fact.

### Fixes

- Discord link is fully optional: installing the extension without a `discord_link_manifest` no
  longer attempts to initialize Discord bridges or resolve JDA classes.
- The client reports a disabled Discord integration when JDA is unavailable or incomplete,
  instead of propagating a `NoClassDefFoundError` into the server thread.
- The public release artifact now bundles JDA 6.5.0 and its runtime dependencies, while keeping
  Typewriter's `BasicExtension` provided by the engine.

## 0.9 — 2026-08-12

### Renamed

- The extension is now **Discord**, not Community. Directory, package and extension name follow;
  entry names are untouched, because pages serialize them.
- `DiscordClientService` moved out of the link feature into its own `client` package: chat sync,
  console and bug reports all use it, so it never belonged to account linking.

### Webhooks are entries

- Added `webhook_definition`: a Discord destination declared once and referenced everywhere.
  Chat sync, account link and bug reports each used to carry their own copy of the URL, username
  and avatar — changing channel meant editing every manifest, with nothing guaranteeing they
  pointed at the same place.
- `WebhookService` is a Koin singleton, injectable by any extension. The HTTP client used to be
  built by hand in the initializer and handed to each service, so nothing outside could deliver
  a message without being given that instance.
- Bug reports lost their second on/off switch: an empty destination **is** the disabled state.
  Two switches for one effect always end up contradicting each other.
- Permanent role mentions live on the destination; one-off mentions stay the caller's business
  and add up with them.

### Migration

- Pages written before this version are converted on first start: one `webhook_definition` per
  distinct destination — manifests configured identically share a single entry — and the
  manifests repointed at it.
- Every rewritten page is backed up first, under `backup/community-webhook-v1/`. The conversion
  is driven by shape, not by a version number, so running it twice changes nothing.
- A manifest that was switched off keeps its URL on a disabled destination rather than losing it.

### Shop announcements

- Added `shop_notification_manifest`: how a shop transaction reads once it reaches Discord.
- The destination is deliberately absent from that entry — it belongs to the shop, which names it
  on its own definition. One presentation can therefore serve every shop while each still posts
  to its own channel.
- Requires the Shops extension, but does not depend on it: Shops publishes an event and knows
  nothing of Discord, and the listener is registered only when the event class is on the server.
