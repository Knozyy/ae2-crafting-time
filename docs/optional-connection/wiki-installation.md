# Installation on a client or server

AE2 Crafting Time may be installed on both sides, only on the server, only on
the client, or on neither side. Install a build matching your Minecraft version
and loader, together with the listed AE2 dependencies.

| Installation | What happens |
| --- | --- |
| Both | Crafting Time features and estimates appear normally. |
| Server only | Crafting is profiled and history is saved. Players without the mod see AE2's native screens. |
| Client only | The client joins a server without the mod and shows AE2's native screens. Crafting Time features become available again when it joins a supported server. |
| Neither | AE2 runs normally. |

You can switch servers without restarting the client. A server with Crafting Time
does not send its custom packets to clients without Crafting Time, and a client
with Crafting Time does not send them to a server without it. Client preferences
are preserved while unsupported features are hidden.

On Fabric 1.20.1, the guide recipe now makes a marked vanilla book. Older
`ae2craftingtime:guide` items in saved inventories cannot be migrated after
the old item ID is removed. Back up worlds and player data before upgrading;
those books may be lost when inventories load. Craft new guide books afterward.
A client without Crafting Time can join a Crafting Time server and use AE2 normally.
