# System icons

The PNG files in this directory are 64 x 64 transparent system icons used for
general-purpose accounts and finance features. Their editable SVG sources are
stored in `source/`. Account icon keys remain compatible with the database
`accounts.icon` value.

The 24 finance feature glyphs registered in `FeatureIcon/index.vue` are original
project drawings, with a shared 24-unit viewBox, 1.7-unit rounded strokes and
translucent fills. Each function has a distinct silhouette: receipts with a
return arrow for recoveries, bank + clock for term deposits, a target for savings,
a globe for global markets, dollar + candlesticks for US markets, shield + yield
for dividend strategy, calendar + coins for dividend forecasts, and calculator +
flag for financial independence. Forecast charts distinguish projected movement
with dashes and break-even with a marked baseline crossing.

After editing their SVGs, regenerate the 64 x 64 PNGs from `web/`:

```bash
node scripts/render-feature-icons.mjs
```

This uses the existing Playwright Chromium installation and updates only the
feature assets registered in the component. The four matching finance homepage
shortcuts share these assets.

The remaining account glyphs are adapted from the Solar icon set by 480 Design through Iconify.
The gold-bars glyph is adapted from Streamline's Ultimate free icons through
Iconify. Both sets are licensed under CC BY 4.0:

- https://www.figma.com/community/file/1166831539721848736
- https://github.com/webalys-hq/streamline-vectors
- https://creativecommons.org/licenses/by/4.0/
