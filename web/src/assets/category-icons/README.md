# Shared category icons

The PNG files in this directory are transparent foreground glyphs for category surfaces across the finance and food modules. The rounded background is rendered by `CategoryIcon` from the category color returned by the API.
Use `@/components/common/CategoryIcon/index.vue` instead of rendering category emoji or text glyphs directly.
The SVG sources are kept in `source/` so the assets can be regenerated at the same 64px size.
