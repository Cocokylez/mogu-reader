package com.tome.reader.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Exact measurements transcribed from the Tome Glass design canvas
 * (`Tome Glass.dc.html`).
 *
 * The design specifies every size, weight and letter-spacing explicitly. Reaching
 * for `MaterialTheme.typography` instead — its `labelMedium` is 14sp where the
 * design asks for 10.5sp — is what pulled the first implementation visibly off
 * the mockup. Everything the redesign renders should come from here so the drift
 * cannot creep back in.
 *
 * Values are 1:1 with the CSS: a `px` in the design becomes a `dp`/`sp` here,
 * because the mockup is authored at the 402dp-wide phone frame it targets.
 */
object GlassSpec {

    // ── screen frame ────────────────────────────────────────────────────────
    /** `padding:62px 0 150px` on every scrolling tab body. */
    val ScreenTopPadding = 62.dp
    val ScreenBottomPadding = 150.dp

    // ── header ──────────────────────────────────────────────────────────────
    /** `padding:14px 12px 6px 16px` with `gap:8px`. */
    val HeaderStart = 16.dp
    val HeaderEnd = 12.dp
    val HeaderTop = 14.dp
    val HeaderBottom = 6.dp
    val HeaderGap = 8.dp

    /** Square glass action buttons beside the title. */
    val HeaderButton = 38.dp
    val HeaderButtonRadius = 12.dp
    val HeaderIcon = 19.dp

    // ── chips ───────────────────────────────────────────────────────────────
    val ChipRowGap = 8.dp
    val ChipRowTop = 10.dp
    val ChipRowSide = 16.dp
    val ChipRowBottom = 4.dp
    val ChipPaddingH = 14.dp
    val ChipPaddingV = 7.dp
    val ChipRadius = 99.dp

    // ── continue-reading hero ───────────────────────────────────────────────
    val HeroMarginTop = 12.dp
    val HeroMarginSide = 16.dp
    val HeroMarginBottom = 4.dp
    val HeroRadius = 22.dp
    val HeroPadding = 14.dp
    val HeroGap = 14.dp
    val HeroCoverWidth = 66.dp
    val HeroCoverHeight = 99.dp
    val HeroCoverRadius = 10.dp
    val HeroPlayButton = 46.dp
    val HeroProgressHeight = 4.dp
    val HeroTextGap = 5.dp

    // ── layout switcher ─────────────────────────────────────────────────────
    val SwitcherMarginTop = 14.dp
    val SwitcherMarginSide = 16.dp
    val SwitcherMarginBottom = 2.dp
    val SwitcherPadding = 4.dp
    val SwitcherRadius = 14.dp
    val SwitcherGap = 4.dp
    val SwitcherSegmentRadius = 10.dp
    val SwitcherSegmentTop = 7.dp
    val SwitcherSegmentBottom = 6.dp

    // ── library grid ────────────────────────────────────────────────────────
    /** `grid-template-columns:repeat(3,minmax(0,1fr))` — a fixed 3-up grid. */
    const val GridColumns = 3
    val GridPadding = 16.dp
    val GridRowGap = 14.dp
    val GridColumnGap = 12.dp
    val CoverRadius = 12.dp
    const val CoverAspect = 0.67f
    val CoverBadgeRadius = 6.dp
    val CoverProgressHeight = 3.dp

    // ── browse / source rails ───────────────────────────────────────────────
    // Source rail covers are wider and slightly squarer than library covers:
    // `width:112px`, `aspect-ratio:0.7`, `border-radius:13px`.
    val BrowseItemWidth = 112.dp
    const val BrowseCoverAspect = 0.70f
    val BrowseCoverRadius = 13.dp
    /** `0 8px 20px rgba(0,0,0,0.34)` */
    val ShadowBrowseCover = 10.dp
    val BrowseRailGap = 11.dp

    // ── shelves ─────────────────────────────────────────────────────────────
    val ShelfItemWidth = 98.dp
    val ShelfGap = 11.dp
    val ShelfBottomGap = 18.dp

    // ── list rows ───────────────────────────────────────────────────────────
    val ListGap = 9.dp
    val ListRowRadius = 16.dp
    val ListRowPadding = 10.dp
    val ListCoverWidth = 44.dp
    val ListCoverHeight = 66.dp
    val ListCoverRadius = 8.dp

    // ── card deck ───────────────────────────────────────────────────────────
    val DeckCardWidth = 212.dp
    val DeckCardRadius = 20.dp
    val DeckStep = 26.dp
    val DeckButton = 40.dp
    const val DeckScaleStep = 0.06f
    const val DeckAlphaStep = 0.22f

    // ── floating action button ──────────────────────────────────────────────
    val FabSize = 56.dp
    val FabRadius = 20.dp
    val FabEnd = 18.dp
    val FabBottom = 112.dp

    // ── bottom navigation pill ──────────────────────────────────────────────
    val NavSideInset = 16.dp
    val NavBottomInset = 34.dp
    val NavPadding = 6.dp
    val NavRadius = 26.dp
    val NavTabHeight = 52.dp
    val NavTabRadius = 20.dp
    val NavIcon = 21.dp
    val NavTabGap = 3.dp

    /**
     * Bottom clearance for scrolling content so the last row clears the floating
     * nav pill. Content deliberately scrolls *underneath* the pill — that is what
     * makes its translucency read as glass; reserving space above it instead
     * leaves it sitting on flat background and looking solid.
     */
    val NavClearance = 112.dp

    // ── section labels / stats ──────────────────────────────────────────────
    val SectionLabelTop = 20.dp
    val StatsRadius = 18.dp

    // ── elevation ───────────────────────────────────────────────────────────
    // The design carries a heavy, coloured shadow on almost every raised
    // surface, and two of them glow in the accent colour rather than in black.
    // Dropping these is what made the first pass read flat.
    /** `0 10px 28px rgba(124,109,249,0.5)` — accent glow. */
    val ShadowFab = 10.dp
    /** `0 8px 20px rgba(124,109,249,0.5)` — accent glow. */
    val ShadowPlay = 10.dp
    /** `0 14px 36px rgba(0,0,0,0.42)` */
    val ShadowNav = 18.dp
    /** `0 12px 32px rgba(0,0,0,0.30)` */
    val ShadowHero = 16.dp
    /** `0 6px 16px rgba(0,0,0,0.32)` */
    val ShadowCover = 8.dp
    /** `0 24px 50px rgba(0,0,0,0.50)` */
    val ShadowDeck = 26.dp
    /** `0 16px 44px rgba(0,0,0,0.55)` */
    val ShadowReaderDock = 22.dp

    // ── type ────────────────────────────────────────────────────────────────
    // `font-size:27px;font-weight:800;letter-spacing:-0.6px`
    val ScreenTitle = TextStyle(fontSize = 27.sp, fontWeight = FontWeight.W800, letterSpacing = (-0.6).sp)
    val HeroKicker = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.W700, letterSpacing = 1.1.sp)
    val HeroTitle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.2).sp)
    val HeroMeta = TextStyle(fontSize = 12.sp)
    val Chip = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W600)
    val SortChip = TextStyle(fontSize = 12.5f.sp, fontWeight = FontWeight.W700)
    val CategoryChip = TextStyle(fontSize = 12.5f.sp, fontWeight = FontWeight.W600)
    val SwitcherLabel = TextStyle(fontSize = 10.5f.sp, fontWeight = FontWeight.W700, letterSpacing = 0.2.sp)
    val GridTitle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600)
    val GridMeta = TextStyle(fontSize = 10.5f.sp, fontWeight = FontWeight.W600)
    val ShelfItemTitle = TextStyle(fontSize = 11.5f.sp, fontWeight = FontWeight.W600)
    val ShelfTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.2).sp)
    val ShelfCount = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600)
    val ListTitle = TextStyle(fontSize = 13.5f.sp, fontWeight = FontWeight.W600)
    val ListMeta = TextStyle(fontSize = 11.sp)
    val ListStatus = TextStyle(fontSize = 11.5f.sp, fontWeight = FontWeight.W700)
    val CoverBadge = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.W800, letterSpacing = 0.6.sp)
    val SectionLabel = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.W800, letterSpacing = 1.2.sp)
    val NavLabel = TextStyle(fontSize = 9.5f.sp, fontWeight = FontWeight.W700, letterSpacing = 0.2.sp)
    // Browse: `font-size:17px;font-weight:750;letter-spacing:-0.3px` for the source
    // name, with a 12px/650 accent "See all" beside it.
    val SourceName = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.3).sp)
    val SeeAll = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600)
    val SourceTag = TextStyle(fontSize = 8.5f.sp, fontWeight = FontWeight.W800, letterSpacing = 0.7.sp)
    val KindPill = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.W700)
    val BrowseTitle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600)
    val BrowseAuthor = TextStyle(fontSize = 10.5f.sp)

    val StatValue = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.W800, letterSpacing = (-0.4).sp)
    val StatLabel = TextStyle(fontSize = 9.5f.sp, fontWeight = FontWeight.W700, letterSpacing = 0.9.sp)
    // Settings rows: `font-size:14px;font-weight:600` labels with
    // `font-size:12.5px;font-weight:650` values.
    val SettingLabel = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600)
    val SettingValue = TextStyle(fontSize = 12.5f.sp, fontWeight = FontWeight.W600)

    // History rows: 13.5/650 title, 11.5 chapter line, 10.5/600 meta.
    val HistoryChapter = TextStyle(fontSize = 11.5f.sp)
    val HistoryMeta = TextStyle(fontSize = 10.5f.sp, fontWeight = FontWeight.W600)

    // Series chapter rows: 11.5/800 number, 13.5/600 title, 10.5 date, 11/800 status.
    val ChapterNumber = TextStyle(fontSize = 11.5f.sp, fontWeight = FontWeight.W800)
    val ChapterDate = TextStyle(fontSize = 10.5f.sp)
    val ChapterStatus = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W800)

    val DeckLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W700, letterSpacing = 0.4.sp)
    val SearchPlaceholder = TextStyle(fontSize = 14.sp)

    // Cover initials scale with the surface they sit on.
    val InitialsGrid = 22.sp
    val InitialsShelf = 20.sp
    val InitialsList = 14.sp
    val InitialsHero = 19.sp
    val InitialsDeck = 44.sp
}
