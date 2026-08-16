# Discord Extension

![Java Version](https://img.shields.io/badge/Java-21-orange)
![Target](https://img.shields.io/badge/Target-Paper%20%2F%20Folia-blue)

Everything that crosses between your server and Discord, on one reusable destination.

> Formerly published as **Community Extension**. The extension, package and directory were renamed
> in v0.9; entry names are unchanged, and pages written before that version are migrated on first
> start.

---

## Features

### Webhook destinations

`webhook_definition` declares a Discord destination once — URL, username, avatar, permanent role
mentions — and every feature references it. Changing channel is a single edit instead of one per
manifest.

`WebhookService` is a Koin singleton, so any extension can deliver a message to a destination
without owning an HTTP client.

### Account link

Verify accounts and synchronise ranks between Minecraft and Discord.

### Chat sync

Relay in-game chat to a Discord channel, and back.

### Console channel

Stream console output to a private channel.

### Bug reports

In-game reporting menus that post to Discord as embeds or forum threads. An empty destination is
the disabled state — there is no second on/off switch to contradict it.

### Shop announcements

`shop_notification_manifest` describes how a shop transaction reads once it reaches Discord. The
destination is deliberately not part of it: it belongs to the shop, which names it on its own
definition, so one presentation serves every shop while each posts to its own channel.

Requires the Shops extension for this feature, but does not depend on it: Shops publishes an event
and knows nothing about Discord, and the listener is registered only when that event class is
present on the server.

### Fact webhooks

`webhook_fact_event` publishes a message whenever a Typewriter fact changes. The event can run once
per player, once per Typewriter group, or once globally, and can optionally be restricted to a
Typewriter audience. Message content, embeds, fields, forum thread names and role mentions are
configurable with `{player}`, `{fact}`, `{previous_value}`, `{new_value}`, `{change}`, `{group}` and
`{players}` placeholders.

The event uses Typewriter's fact tracker, so grouped facts and custom fact implementations are read
through the same engine path as the rest of the server. A Discord webhook can publish messages but
cannot invoke a slash command; command automation should be handled by a Discord bot consuming the
published message.

---

## Migration from Community

Pages written before v0.9 carry their webhook as an inline object. On first start they are
converted: one `webhook_definition` per distinct destination — manifests configured identically
share a single entry — and the manifests repointed at it.

Every rewritten page is backed up first, under `backup/community-webhook-v1/`. The conversion is
driven by the shape of the data rather than a version number, so running it twice changes nothing.
A manifest that was switched off keeps its URL on a disabled destination rather than losing it.

---

## Configuration

Configured through Typewriter's manifest system, in the web editor.
Full documentation available at [BTC Studio Docs](https://docs.borntocraftstudio.net/extensions/free/discord/).
