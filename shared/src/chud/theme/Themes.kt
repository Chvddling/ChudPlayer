package chud.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/*
 * ChudPlayer themes, shared by the Windows app and the phone remote
 * (both builds compile this folder). Everything is plain Compose colours and
 * brushes, so each platform only maps the fonts.
 */

/** Texture drawn behind panels. */
enum class Pattern { NONE, HALFTONE, BRUSHED, CHECKER, SCANLINES }

/** Font families; each platform maps these to fonts it has. */
enum class FontKind { DEFAULT, CONDENSED, MONO, TAHOMA, BLACK }

data class ChudTheme(
    val id: String,
    val name: String,
    // colours
    val bgDeep: Color,
    val bgContent: Color,
    val rowA: Color,
    val rowB: Color,
    val text: Color,
    val textBright: Color,
    val textDim: Color,
    val label: Color,
    val link: Color,
    val accent: Color,
    val lcdText: Color,
    val lcdDim: Color,
    val selectionBorder: Color,
    /** Text on [selection] / [rowSelected] / [accentBrush]. */
    val onSelection: Color,
    val onAccent: Color,
    val outline: Color,
    /** Widget outline (black on the glossy themes, white ink on Manga). */
    val edge: Color,
    val edgeWidth: Float,
    val track: Color,
    val field: Color,
    /** Glow behind LCD text and the play orb; Transparent = none. */
    val glow: Color,
    // brushes
    val bar: Brush,
    val button: Brush,
    val accentBrush: Brush,
    val selection: Brush,
    val rowSelected: Brush,
    val header: Brush,
    val serviceBar: Brush,
    val statusBar: Brush,
    val sidebar: Brush,
    val content: Brush,
    val lcd: Brush,
    val progress: Brush,
    val frame: Brush,
    val titleBar: Brush,
    val caption: Brush,
    val captionHover: Brush,
    val close: Brush,
    val closeHover: Brush,
    val splitter: Brush,
    val orb: List<Color>,
    val orbIcon: Color,
    val orbSmall: List<Color>,
    val knob: List<Color>,
    // shapes, type, texture
    /** Corner radius of "round" buttons in dp; 999 = pill. */
    val corner: Float,
    val smallCorner: Float,
    val pattern: Pattern = Pattern.NONE,
    val patternColor: Color = Color.Transparent,
    val lcdPattern: Pattern = Pattern.NONE,
    val font: FontKind = FontKind.DEFAULT,
    val titleFont: FontKind = FontKind.DEFAULT,
    val lcdFont: FontKind = FontKind.DEFAULT,
    val upperTitles: Boolean = false,
    /** Tilt of the cover art in degrees (Scrapyard, Manga). */
    val artTilt: Float = 0f,
    /** Hard offset shadow behind the cover art; Transparent = soft drop shadow. */
    val artShadow: Color = Color.Transparent,
)

fun gloss(top: Color, upper: Color, lower: Color, bottom: Color): Brush =
    Brush.verticalGradient(0f to top, 0.5f to upper, 0.51f to lower, 1f to bottom)

fun flat(c: Color): Brush = Brush.verticalGradient(listOf(c, c))
fun vert(a: Color, b: Color): Brush = Brush.verticalGradient(listOf(a, b))
private fun c(argb: Long) = Color(argb)

// ------------------------------------------------------------------ hue shifting

/** Same lightness and saturation, new hue (for the Obsidian colour versions). Greys stay grey. */
fun Color.withHue(hueDeg: Float): Color {
    val r = red; val g = green; val b = blue
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d < 0.0001f) return this
    val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
    if (s < 0.3f) return this
    val h = hueDeg / 360f
    val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
    val p = 2 * l - q
    fun hk(t0: Float): Float {
        var t = t0
        if (t < 0) t += 1f
        if (t > 1) t -= 1f
        return when {
            t < 1f / 6 -> p + (q - p) * 6 * t
            t < 1f / 2 -> q
            t < 2f / 3 -> p + (q - p) * (2f / 3 - t) * 6
            else -> p
        }
    }
    return Color(hk(h + 1f / 3), hk(h), hk(h - 1f / 3), alpha)
}

// ------------------------------------------------------------------ the themes

object Themes {

    val smoked = ChudTheme(
        id = "smoked", name = "Smoked Glass",
        bgDeep = c(0xFF0D1117), bgContent = c(0xFF151B23), rowA = c(0xFF10151C), rowB = c(0xFF151B23),
        text = c(0xFFD7E3EF), textBright = c(0xFFEEF4FA), textDim = c(0xFF93A8BD), label = c(0xFF7F98B0),
        link = c(0xFF8CC8FF), accent = c(0xFF3E9BEA), lcdText = c(0xFFBFE3FF), lcdDim = c(0xFF86A7C4),
        selectionBorder = c(0xFF3D7FBF), onSelection = Color.White, onAccent = Color.White,
        outline = c(0xFF3A4552), edge = Color.Black, edgeWidth = 1f, track = c(0xFF05080B), field = c(0xFF0B1118),
        glow = c(0x995AAAFF),
        bar = gloss(c(0xFF3B4656), c(0xFF222A35), c(0xFF10151C), c(0xFF1A212B)),
        button = gloss(c(0xFF4A5563), c(0xFF2B333E), c(0xFF1B2129), c(0xFF2C343E)),
        accentBrush = gloss(c(0xFF8CCBFF), c(0xFF3A8EE0), c(0xFF1E6BC2), c(0xFF4A9DEA)),
        selection = gloss(c(0x736EB4FF), c(0x4D3278D2), c(0x591E5AB4), c(0x66468CE6)),
        rowSelected = gloss(c(0xFF3F8AD6), c(0xFF1F5FAE), c(0xFF154F99), c(0xFF2A6FC0)),
        header = gloss(c(0xFF34404F), c(0xFF242C37), c(0xFF171D25), c(0xFF1F262F)),
        serviceBar = vert(c(0xFF1F2731), c(0xFF151B23)),
        statusBar = vert(c(0xFF252D38), c(0xFF151B22)),
        sidebar = vert(c(0xFF1A212B), c(0xFF141A22)),
        content = vert(c(0xFF151B23), c(0xFF0D1117)),
        lcd = vert(c(0xFF0F1A26), c(0xFF06101A)),
        progress = Brush.horizontalGradient(listOf(c(0xFF2D8DE0), c(0xFF9FD8FF))),
        frame = Brush.verticalGradient(0f to c(0xFF3A4757), 0.04f to c(0xFF2A3441), 0.2f to c(0xFF1E2630), 1f to c(0xFF151B23)),
        titleBar = flat(Color.Transparent),
        caption = gloss(c(0xFF5A6575), c(0xFF353F4C), c(0xFF1E252E), c(0xFF39434F)),
        captionHover = gloss(c(0xFFA9D4F7), c(0xFF4F8BC6), c(0xFF2D6AAE), c(0xFF6AA8E0)),
        close = gloss(c(0xFFE8907C), c(0xFFC8452B), c(0xFFA51F08), c(0xFFCF4A2E)),
        closeHover = gloss(c(0xFFFFB9A6), c(0xFFEC5F40), c(0xFFC92D10), c(0xFFF07050)),
        splitter = vert(c(0xFF39434F), c(0xFF1A2027)),
        orb = listOf(c(0xFFD8F0FF), c(0xFF3E9BEA), c(0xFF0E4C90), c(0xFF062A52)),
        orbIcon = Color.White,
        orbSmall = listOf(c(0xFF7A8796), c(0xFF2A323D), c(0xFF11161C)),
        knob = listOf(Color.White, c(0xFF9CB3C9)),
        corner = 999f, smallCorner = 3f,
    )

    val slate = ChudTheme(
        id = "slate", name = "Slate",
        bgDeep = c(0xFF0D0F13), bgContent = c(0xFF101217), rowA = c(0xFF0F1115), rowB = c(0xFF121419),
        text = c(0xFFECEEF3), textBright = Color.White, textDim = c(0xFF9097A8), label = c(0xFF7D8496),
        link = c(0xFFB4A9FF), accent = c(0xFF8B7CFF), lcdText = c(0xFFECEEF3), lcdDim = c(0xFF9097A8),
        selectionBorder = c(0xFF8B7CFF), onSelection = Color.White, onAccent = Color.White,
        outline = c(0xFF2A2E37), edge = c(0xFF23262E), edgeWidth = 1f, track = c(0xFF262A33), field = c(0xFF16181E),
        glow = Color.Transparent,
        bar = flat(c(0xFF0D0F13)), button = flat(c(0xFF1A1D24)), accentBrush = flat(c(0xFF4A3FA8)),
        selection = flat(c(0xFF1E1B3A)), rowSelected = flat(c(0xFF231E4A)), header = flat(c(0xFF101217)),
        serviceBar = flat(c(0xFF0D0F13)), statusBar = flat(c(0xFF0D0F13)), sidebar = flat(c(0xFF0D0F13)),
        content = flat(c(0xFF0D0F13)), lcd = flat(c(0xFF121419)), progress = flat(c(0xFF8B7CFF)),
        frame = flat(c(0xFF0D0F13)), titleBar = flat(c(0xFF0D0F13)),
        caption = flat(c(0xFF0D0F13)), captionHover = flat(c(0xFF1F2229)),
        close = flat(c(0xFF0D0F13)), closeHover = flat(c(0xFFE5484D)),
        splitter = flat(c(0xFF16181E)),
        orb = listOf(c(0xFFECEEF3), c(0xFFECEEF3), c(0xFFE2E5EC), c(0xFFD0D4DD)), orbIcon = c(0xFF0D0F13),
        orbSmall = listOf(c(0xFF1F2229), c(0xFF1A1D24), c(0xFF1A1D24)),
        knob = listOf(c(0xFFECEEF3), c(0xFFECEEF3)),
        corner = 10f, smallCorner = 8f,
    )

    val scrapyard = ChudTheme(
        id = "scrapyard", name = "Scrapyard",
        bgDeep = c(0xFF121310), bgContent = c(0xFF161714), rowA = c(0xFF161714), rowB = c(0xFF1B1C19),
        text = c(0xFFDAD7CB), textBright = c(0xFFEEEBDF), textDim = c(0xFF9A978B), label = c(0xFFE9A23B),
        link = c(0xFFE9A23B), accent = c(0xFFE9A23B), lcdText = c(0xFF9CFF57), lcdDim = c(0xFF5FA83A),
        selectionBorder = c(0xFF2C4A17), onSelection = c(0xFFEEEBDF), onAccent = c(0xFF1A1306),
        outline = c(0xFF4A4B44), edge = Color.Black, edgeWidth = 1f, track = c(0xFF0A0A08), field = c(0xFF0C0C0A),
        glow = c(0x8C9CFF57),
        bar = vert(c(0xFF34352F), c(0xFF1E1F1B)),
        button = vert(c(0xFF4A4B45), c(0xFF2B2C28)),
        accentBrush = vert(c(0xFFD89A35), c(0xFF9B6514)),
        selection = vert(c(0xFF6E4A12), c(0xFF4A300A)),
        rowSelected = flat(c(0xFF14230B)),
        header = vert(c(0xFF34352F), c(0xFF24251F)),
        serviceBar = vert(c(0xFF2C2D28), c(0xFF1E1F1B)),
        statusBar = vert(c(0xFF2C2D28), c(0xFF1A1B17)),
        sidebar = vert(c(0xFF1F201C), c(0xFF1B1C19)),
        content = flat(c(0xFF161714)),
        lcd = flat(c(0xFF0B1208)),
        progress = flat(c(0xFF9CFF57)),
        frame = flat(c(0xFF232420)),
        titleBar = vert(c(0xFF3D3E38), c(0xFF262722)),
        caption = vert(c(0xFF4A4B45), c(0xFF2B2C28)), captionHover = vert(c(0xFF6B6C64), c(0xFF3F403A)),
        close = vert(c(0xFF7A2A16), c(0xFF4A1206)), closeHover = vert(c(0xFFA3381C), c(0xFF6A1A08)),
        splitter = Brush.linearGradient(
            0f to c(0xFFE9A23B), 0.25f to c(0xFFE9A23B), 0.25f to c(0xFF1A1A17), 0.5f to c(0xFF1A1A17),
            0.5f to c(0xFFE9A23B), 0.75f to c(0xFFE9A23B), 0.75f to c(0xFF1A1A17), 1f to c(0xFF1A1A17),
            start = Offset(0f, 0f), end = Offset(22f, 22f), tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
        ),
        orb = listOf(c(0xFFE0A845), c(0xFFB97A1C), c(0xFF8A5A12), c(0xFF6E4208)), orbIcon = c(0xFF1A1306),
        orbSmall = listOf(c(0xFF5A5B55), c(0xFF4A4B45), c(0xFF2B2C28)),
        knob = listOf(c(0xFF9A9A90), c(0xFF4A4B45)),
        corner = 2f, smallCorner = 2f,
        pattern = Pattern.BRUSHED, patternColor = c(0x0DFFFFFF), lcdPattern = Pattern.SCANLINES,
        font = FontKind.CONDENSED, titleFont = FontKind.CONDENSED, lcdFont = FontKind.MONO, upperTitles = true,
        artTilt = -1.2f, artShadow = c(0x99000000),
    )

    /** Piano-black gloss with gel buttons; [hue] picks the gel colour (crimson is the original). */
    private fun obsidian(id: String, name: String, hue: Float?): ChudTheme {
        fun h(argb: Long) = if (hue == null) c(argb) else c(argb).withHue(hue)
        val gel = gloss(h(0xFFFF8A8A), h(0xFFE8283C), h(0xFFB3001B), h(0xFFE2263C))
        val gelHover = gloss(h(0xFFFFB0B0), h(0xFFFF4A5C), h(0xFFD01030), h(0xFFFF4A5C))
        return ChudTheme(
            id = id, name = name,
            bgDeep = c(0xFF070708), bgContent = c(0xFF0F0F12), rowA = c(0xFF0B0B0D), rowB = c(0xFF111115),
            text = c(0xFFEDEDF2), textBright = Color.White, textDim = c(0xFFA3A3AF), label = c(0xFF8F8F9C),
            link = h(0xFFFF8A96), accent = h(0xFFFF2D46), lcdText = Color.White, lcdDim = c(0xFFA3A3AF),
            selectionBorder = h(0xFF5A0010), onSelection = Color.White, onAccent = Color.White,
            outline = c(0xFF34343C), edge = Color.Black, edgeWidth = 1f, track = Color.Black, field = c(0xFF050506),
            glow = h(0x99FF3C5A),
            bar = gloss(c(0xFF2A2A31), c(0xFF141417), c(0xFF060607), c(0xFF121215)),
            button = gloss(c(0xFF5A5A62), c(0xFF2A2A30), c(0xFF111115), c(0xFF26262C)),
            accentBrush = gel,
            selection = gel,
            rowSelected = gloss(h(0x59FF5A6E), h(0x40B4001E), h(0x4D780014), h(0x4DC81E3C)),
            header = gloss(c(0xFF26262C), c(0xFF16161A), c(0xFF0B0B0D), c(0xFF151518)),
            serviceBar = vert(c(0xFF1A1A1F), c(0xFF0F0F12)),
            statusBar = vert(c(0xFF1F1F24), c(0xFF0B0B0D)),
            sidebar = vert(c(0xFF141417), c(0xFF0A0A0C)),
            content = vert(c(0xFF0F0F12), c(0xFF070708)),
            lcd = vert(c(0xFF050506), c(0xFF141418)),
            progress = Brush.horizontalGradient(listOf(h(0xFFB3001B), h(0xFFFF5A6E))),
            frame = vert(c(0xFF17171B), c(0xFF0B0B0D)),
            titleBar = gloss(c(0xFF26262C), c(0xFF1B1B20), c(0xFF101013), c(0xFF16161A)),
            caption = gloss(c(0xFF5A5A62), c(0xFF2A2A30), c(0xFF111115), c(0xFF26262C)),
            captionHover = gloss(c(0xFF7A7A84), c(0xFF3C3C44), c(0xFF1C1C22), c(0xFF34343C)),
            close = gel, closeHover = gelHover,
            splitter = gloss(c(0xFF2A2A31), c(0xFF17171B), c(0xFF0D0D10), c(0xFF141417)),
            orb = listOf(h(0xFFFFD0D5), h(0xFFFF4058), h(0xFFB0001A), h(0xFF4A0009)), orbIcon = Color.White,
            orbSmall = listOf(c(0xFF5A5A62), c(0xFF2A2A30), c(0xFF111115)),
            knob = listOf(Color.White, c(0xFFA8A8B4)),
            corner = 999f, smallCorner = 12f,
        )
    }

    val obsidian = obsidian("obsidian", "Obsidian (crimson)", null)
    val emerald = obsidian("obsidian-emerald", "Obsidian Emerald", 145f)
    val sapphire = obsidian("obsidian-sapphire", "Obsidian Sapphire", 212f)
    val amethyst = obsidian("obsidian-amethyst", "Obsidian Amethyst", 278f)

    val dialUp = ChudTheme(
        id = "dialup", name = "Dial-Up",
        bgDeep = c(0xFF101010), bgContent = c(0xFF1D1D1D), rowA = c(0xFF1A1A1A), rowB = c(0xFF202020),
        text = c(0xFFDADADA), textBright = Color.White, textDim = c(0xFF9C9C9C), label = c(0xFFFFD400),
        link = c(0xFF7CFF00), accent = c(0xFF7CFF00), lcdText = c(0xFF7CFF00), lcdDim = c(0xFF3E9A00),
        selectionBorder = Color.White, onSelection = Color.White, onAccent = c(0xFF7CFF00),
        outline = c(0xFF5A5A5A), edge = c(0xFF6A6A6A), edgeWidth = 2f, track = Color.Black, field = c(0xFF0B0B0B),
        glow = Color.Transparent,
        bar = flat(c(0xFF262626)), button = flat(c(0xFF383838)), accentBrush = flat(c(0xFF0F2A00)),
        selection = flat(c(0xFF000080)), rowSelected = flat(c(0xFF000080)), header = flat(c(0xFF2E2E2E)),
        serviceBar = flat(c(0xFF262626)), statusBar = flat(c(0xFF262626)), sidebar = flat(c(0xFF181818)),
        content = flat(c(0xFF1D1D1D)), lcd = flat(Color.Black),
        progress = Brush.horizontalGradient(listOf(c(0xFF1F6A00), c(0xFF7CFF00))),
        frame = flat(c(0xFF1D1D1D)),
        titleBar = Brush.horizontalGradient(0f to c(0xFF4B0F63), 0.7f to c(0xFF16001F), 1f to Color.Black),
        caption = flat(c(0xFF3A3A3A)), captionHover = flat(c(0xFF4C4C4C)),
        close = flat(c(0xFF3A3A3A)), closeHover = flat(c(0xFF8A1010)),
        splitter = Brush.horizontalGradient(
            0f to c(0xFF555555), 0.5f to c(0xFF555555), 0.5f to c(0xFF262626), 1f to c(0xFF262626),
            startX = 0f, endX = 4f, tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
        ),
        orb = listOf(c(0xFF4A4A4A), c(0xFF383838), c(0xFF383838), c(0xFF2A2A2A)), orbIcon = c(0xFFDADADA),
        orbSmall = listOf(c(0xFF484848), c(0xFF383838), c(0xFF2A2A2A)),
        knob = listOf(c(0xFF999999), c(0xFF777777)),
        corner = 0f, smallCorner = 0f,
        pattern = Pattern.CHECKER, patternColor = c(0x0AFFFFFF),
        font = FontKind.TAHOMA, titleFont = FontKind.TAHOMA, lcdFont = FontKind.MONO,
    )

    val manga = ChudTheme(
        id = "manga", name = "Manga",
        bgDeep = c(0xFF050505), bgContent = c(0xFF0B0B0B), rowA = c(0xFF0B0B0B), rowB = c(0xFF111111),
        text = c(0xFFF4F1EA), textBright = Color.White, textDim = c(0xFFB9B4A8), label = c(0xFFF4F1EA),
        link = c(0xFFFF6B5E), accent = c(0xFFE8322E), lcdText = c(0xFFF4F1EA), lcdDim = c(0xFFB9B4A8),
        selectionBorder = c(0xFFE8322E), onSelection = c(0xFF0B0B0B), onAccent = c(0xFF0B0B0B),
        outline = c(0xFFF4F1EA), edge = c(0xFFF4F1EA), edgeWidth = 2f, track = c(0xFF0B0B0B), field = c(0xFF0B0B0B),
        glow = Color.Transparent,
        bar = flat(c(0xFF0B0B0B)), button = flat(c(0xFF0B0B0B)), accentBrush = flat(c(0xFFF4F1EA)),
        selection = flat(c(0xFFF4F1EA)), rowSelected = flat(c(0xFFF4F1EA)), header = flat(c(0xFF151515)),
        serviceBar = flat(c(0xFF0B0B0B)), statusBar = flat(c(0xFF0B0B0B)), sidebar = flat(c(0xFF0B0B0B)),
        content = flat(c(0xFF0B0B0B)), lcd = flat(c(0xFF0B0B0B)), progress = flat(c(0xFFE8322E)),
        frame = flat(c(0xFF0B0B0B)), titleBar = flat(c(0xFF0B0B0B)),
        caption = flat(c(0xFF0B0B0B)), captionHover = flat(c(0xFF2A2A2A)),
        close = flat(c(0xFFE8322E)), closeHover = flat(c(0xFFFF5A50)),
        splitter = flat(c(0xFF050505)),
        orb = listOf(c(0xFFF4F1EA), c(0xFFF4F1EA), c(0xFFF4F1EA), c(0xFFE6E2D8)), orbIcon = c(0xFF0B0B0B),
        orbSmall = listOf(c(0xFF0B0B0B), c(0xFF0B0B0B), c(0xFF0B0B0B)),
        knob = listOf(c(0xFFE8322E), c(0xFFE8322E)),
        corner = 2f, smallCorner = 2f,
        pattern = Pattern.HALFTONE, patternColor = c(0xFF2B2B2B),
        titleFont = FontKind.BLACK, lcdFont = FontKind.BLACK, upperTitles = true,
        artTilt = -2f, artShadow = c(0xFFE8322E),
    )

    val all: List<ChudTheme> = listOf(smoked, slate, scrapyard, obsidian, emerald, sapphire, amethyst, dialUp, manga)

    fun byId(id: String?): ChudTheme = all.firstOrNull { it.id == id } ?: smoked
}

/** The theme in use; reading it inside a composable recomposes when it changes. */
object ThemeState {
    var current by mutableStateOf(Themes.smoked)
}

// ------------------------------------------------------------------ textures

/** Draws the theme's texture behind this element (halftone dots, brushed metal, checker, scanlines). */
fun Modifier.themePattern(pattern: Pattern, color: Color): Modifier =
    if (pattern == Pattern.NONE || color == Color.Transparent) this else drawBehind {
        val w = size.width; val h = size.height
        when (pattern) {
            Pattern.HALFTONE -> {
                val step = 6f * density
                val r = 1.1f * density
                var y = step / 2
                var row = 0
                while (y < h) {
                    var x = if (row % 2 == 0) step / 2 else step
                    while (x < w) { drawCircle(color, r, Offset(x, y)); x += step }
                    y += step; row++
                }
            }
            Pattern.BRUSHED -> {
                var x = 0f
                var i = 0
                while (x < w) {
                    val a = if (i % 3 == 0) color else color.copy(alpha = color.alpha * 0.4f)
                    drawRect(a, Offset(x, 0f), Size(1f, h))
                    x += 2f + (i * 7 % 3); i++
                }
            }
            Pattern.CHECKER -> {
                val s = 4f * density
                var y = 0f; var row = 0
                while (y < h) {
                    var x = if (row % 2 == 0) 0f else s
                    while (x < w) { drawRect(color, Offset(x, y), Size(s, s)); x += 2 * s }
                    y += s; row++
                }
            }
            Pattern.SCANLINES -> {
                var y = 0f
                while (y < h) { drawRect(color, Offset(0f, y), Size(w, 1f)); y += 3f }
            }
            Pattern.NONE -> Unit
        }
    }

/** Upper-cases titles on themes that want it (Scrapyard, Manga). */
fun ChudTheme.title(s: String) = if (upperTitles) s.uppercase() else s
