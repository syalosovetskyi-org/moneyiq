@file:OptIn(ExperimentalComposeUiApi::class)

package org.syalosovetskyi.onemoney.ui.shots

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Автоперевірки верстки на кожному знімку (serg/tasks#3037). НЕ валять тест — лише складають
 * знахідки у [Findings]. Обхід — по unmergedTree кожного кореня (activity, діалоги, шторки).
 *
 * Типи знахідок:
 *  - `ellipsis`       — рядок тексту обрізано еліпсисом;
 *  - `overflow`       — hasVisualOverflow без еліпсиса (текст вилазить за свої межі);
 *  - `word-break`     — слово розірване між рядками;
 *  - `small-target`   — клікабельний вузол менше 48dp по одній зі сторін;
 *  - `overlap`        — перетин видимих меж двох клікабельних/текстових вузлів, не предок-нащадок;
 *  - `offscreen`      — вузол (не прихований скролом) виходить за межі свого вікна;
 *  - `latin`          — рядок інтерфейсу лише латиницею (суми, валюти й дані фікстур — виняток);
 *  - `audit-skip`     — перевірку тексту НЕ виконано (немає розмітки) — це не «ок».
 */
class LayoutAudit(
    private val compose: ComposeContentTestRule,
    private val shot: String,
    /** Дозволені латинські рядки (дані фікстур, валюти), довші — першими. */
    private val latinAllow: List<String>,
) {
    private class Item(
        val node: SemanticsNode,
        /** Межі, обрізані скрол-предками (але не вікном). */
        val scrollVisible: Rect,
        /** Те, що реально видно у вікні. */
        val visible: Rect,
        val ancestors: Set<Int>,
        val clickable: Boolean,
        val text: String?,
    )

    private val seen = HashSet<String>()

    fun run() {
        val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        val minTouch = with(compose.density) { 48.dp.toPx() }
        for (root in roots) auditRoot(root, minTouch)
    }

    private fun auditRoot(root: SemanticsNode, minTouch: Float) {
        val rootBounds = root.boundsInRoot
        val rootArea = rootBounds.width * rootBounds.height
        val items = ArrayList<Item>()
        walk(root, isRootNode = true, rootBounds, Rect(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY), emptySet(), items)

        for (item in items) {
            val b = item.node.boundsInRoot
            val label = labelOf(item.node)
            // За межами вікна (з допуском 1 px). Беремо межі після обрізання скролом: картка,
            // наполовину прокручена за край LazyRow, — не дефект.
            val v = item.scrollVisible
            if (v.left < rootBounds.left - 1f || v.top < rootBounds.top - 1f ||
                v.right > rootBounds.right + 1f || v.bottom > rootBounds.bottom + 1f) {
                add("offscreen", label, b)
            }
            if (item.clickable && (b.width < minTouch - 0.5f || b.height < minTouch - 0.5f)) {
                add("small-target", label, b)
            }
            if (item.text != null) {
                checkText(item.node, label, b)
                checkLatin(item.text, b)
            }
        }

        // Перетини: лише вузли, що не накривають майже все вікно (скрим, фон шторки).
        val candidates = items.filter { (it.clickable || it.text != null) && it.visible.width > 0f &&
            it.visible.height > 0f && it.visible.width * it.visible.height < rootArea * 0.8f }
        for (i in candidates.indices) for (j in i + 1 until candidates.size) {
            val a = candidates[i]; val c = candidates[j]
            if (a.node.id in c.ancestors || c.node.id in a.ancestors) continue
            if (!a.visible.overlaps(c.visible)) continue
            val inter = a.visible.intersect(c.visible)
            if (inter.width > 1f && inter.height > 1f) {
                add("overlap", "${labelOf(a.node)} | ${labelOf(c.node)}", inter)
            }
        }
    }

    /**
     * [scrollClip] — перетин меж скрол-предків: вузол, повністю за ним, прокручено з поля зору,
     * і його не перевіряємо (він не на знімку).
     */
    private fun walk(node: SemanticsNode, isRootNode: Boolean, rootBounds: Rect, scrollClip: Rect, ancestors: Set<Int>, out: MutableList<Item>) {
        val b = node.boundsInRoot
        val config = node.config
        val hidden = config.contains(SemanticsProperties.InvisibleToUser)
        val clipped = if (scrollClip.overlaps(b)) b.intersect(scrollClip) else Rect.Zero
        val onScreen = !hidden && b.width > 0f && b.height > 0f && clipped.width > 0f && clipped.height > 0f
        if (onScreen && !isRootNode) {
            val text = textOf(node)
            out += Item(
                node = node,
                scrollVisible = clipped,
                visible = if (clipped.overlaps(rootBounds)) clipped.intersect(rootBounds) else Rect.Zero,
                ancestors = ancestors,
                clickable = config.contains(SemanticsActions.OnClick),
                text = text,
            )
        }
        val isScroll = config.contains(SemanticsProperties.VerticalScrollAxisRange) ||
            config.contains(SemanticsProperties.HorizontalScrollAxisRange)
        val childClip = if (isScroll) (if (scrollClip.overlaps(b)) b.intersect(scrollClip) else Rect.Zero) else scrollClip
        val childAncestors = ancestors + node.id
        for (child in node.children) walk(child, isRootNode = false, rootBounds, childClip, childAncestors, out)
    }

    private fun textOf(node: SemanticsNode): String? {
        val parts = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() +
            listOfNotNull(node.config.getOrNull(SemanticsProperties.EditableText)?.text)
        val joined = parts.joinToString(" ").trim()
        return joined.ifEmpty { null }
    }

    private fun labelOf(node: SemanticsNode): String =
        textOf(node)
            ?: node.config.getOrNull(SemanticsProperties.TestTag)?.let { "tag:$it" }
            ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")?.let { "cd:$it" }
            ?: node.config.getOrNull(SemanticsProperties.Role)?.let { "role:$it#${node.id}" }
            ?: "node#${node.id}"

    private fun checkText(node: SemanticsNode, label: String, b: Rect) {
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        if (action == null) {
            add("audit-skip", "немає GetTextLayoutResult: $label", b)
            return
        }
        val results = mutableListOf<TextLayoutResult>()
        var ok = false
        compose.runOnUiThread { ok = action(results) }
        val layout = results.firstOrNull()
        if (!ok || layout == null) {
            add("audit-skip", "GetTextLayoutResult не віддав розмітку: $label", b)
            return
        }
        val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        if (ellipsized) add("ellipsis", label, b)
        // Еліпсис майже завжди дає й hasVisualOverflow — окремо пишемо лише переповнення без нього.
        if (!ellipsized && layout.hasVisualOverflow) add("overflow", label, b)

        val text = layout.layoutInput.text.text
        for (word in WORD.findAll(text)) {
            if (word.value.length < 2) continue
            val first = layout.getLineForOffset(word.range.first)
            val last = layout.getLineForOffset(word.range.last)
            if (first != last) add("word-break", "«${word.value}» у «$label» (рядки $first–$last)", b)
        }
    }

    private fun checkLatin(text: String, b: Rect) {
        if (text in latinAllow) return
        var rest = text
        // Дані фікстур / тестові дані: довші — першими, щоб не різати частинами.
        for (allowed in latinAllow) if (allowed.length > 1 && rest.contains(allowed)) rest = rest.replace(allowed, " ")
        rest = CURRENCY_CODE.replace(rest, " ")
        rest = NON_LETTERS.replace(rest, "")
        if (rest.isEmpty()) return
        val hasLatin = rest.any { it in 'A'..'Z' || it in 'a'..'z' }
        val hasCyrillic = rest.any { it in 'Ѐ'..'ӿ' }
        if (hasLatin && !hasCyrillic) add("latin", text, b)
    }

    private fun add(type: String, text: String, b: Rect) {
        val bounds = String.format(Locale.ROOT, "%.0f,%.0f,%.0f,%.0f", b.left, b.top, b.right, b.bottom)
        if (seen.add("$type|$text|$bounds")) Findings.add(shot, type, text, bounds)
    }

    private companion object {
        /** Слово без дефіса: перенос після дефіса («IT-|послуги») розривом не вважаємо. */
        val WORD = Regex("""[\p{L}\p{N}'’]+""")
        val CURRENCY_CODE = Regex("""\b[A-Z]{3}\b""")
        /** Усе, крім літер: цифри, пробіли, пунктуація, символи валют. */
        val NON_LETTERS = Regex("""[^\p{L}]""")
    }
}
