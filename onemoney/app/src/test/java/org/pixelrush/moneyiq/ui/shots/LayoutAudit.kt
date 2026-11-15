@file:OptIn(ExperimentalComposeUiApi::class)

package org.syalosovetskyi.onemoney.ui.shots

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Автоперевірки верстки на кожному знімку (serg/tasks#3037). НЕ валять тест — лише складають
 * знахідки у [Findings]. Обхід — по unmergedTree кожного кореня (activity, діалоги, шторки).
 *
 * Типи знахідок:
 *  - `ellipsis`        — рядок тексту обрізано еліпсисом;
 *  - `overflow`        — текст фактично не вміщається без еліпсиса: рядок ширший за поле
 *                        (getLineRight > width + 1px), обрізано по maxLines, або знизу відрізано
 *                        більше половини рядка. Голий hasVisualOverflow НЕ рахується: він true і
 *                        на однорядкових полях / softWrap=false, які реально вміщаються;
 *  - `word-break`      — слово розірване між рядками;
 *  - `small-target`    — клікабельний вузол менше 48dp, якого не рятує ні
 *                        minimumInteractiveComponentSize, ні клікабельний предок ≥48dp;
 *  - `small-target-m3` — те саме, але це клітинка стандартного M3 DatePicker (40dp за специфікацією
 *                        Material — окремо, щоб не змішувати з нашими кнопками);
 *  - `overlap`         — суттєвий (>20% меншого) перетин двох текстів або двох клікабельних
 *                        вузлів, що не є частинами однієї кнопки/рядка;
 *  - `under-overlay`   — вміст скролу під нерухомим шаром (FAB, нижня панель, шапка) там, куди
 *                        скрол уже не докрутить, — тобто його не відкрити жодним жестом;
 *  - `offscreen`       — вузол (не прихований скролом) виходить за межі свого вікна;
 *  - `latin`           — рядок інтерфейсу лише латиницею (суми, валюти й дані фікстур — виняток);
 *  - `audit-skip`      — перевірку тексту НЕ виконано (текст лише в семантиці, без розмітки) —
 *                        це не «ок»; клітинки M3 DatePicker зведено в один рядок на вікно.
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
        /** Скрол-контейнери-предки, від кореня до вузла. */
        val scrolls: List<Int>,
        val clickable: Boolean,
        val text: String?,
    )

    /** Будь-який розміщений вузол дерева (і невидимий теж) — для перевірок через предків. */
    private class Info(val bounds: Rect, val clickable: Boolean, val merging: Boolean, val horizontalScroll: Boolean)

    private class ScrollBox(val visible: Rect, val vertical: Boolean, val atStart: Boolean, val atEnd: Boolean)

    private val seen = HashSet<String>()

    fun run() {
        val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        val minTouch = with(compose.density) { 48.dp.toPx() }
        val m3Cell = with(compose.density) { 40.dp.toPx() }
        for (root in roots) auditRoot(root, minTouch, m3Cell)
    }

    private fun auditRoot(root: SemanticsNode, minTouch: Float, m3Cell: Float) {
        val rootBounds = root.boundsInRoot
        if (!root.layoutInfo.isPlaced) {
            // walk() відкидає нерозміщені вузли; без цього рядка вікно пройшло б «чистим» непереглянутим.
            add("audit-skip", "корінь вікна не розміщено — вікно не перевірено", rootBounds)
            return
        }
        val rootArea = rootBounds.width * rootBounds.height
        val items = ArrayList<Item>()
        val infos = HashMap<Int, Info>()
        val scrollBoxes = HashMap<Int, ScrollBox>()
        walk(root, isRootNode = true, rootBounds, INFINITE, emptySet(), emptyList(), items, infos, scrollBoxes)

        var m3TextSkipped = 0
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
            val m3 = isM3PickerCell(item, infos, b, m3Cell)
            if (item.clickable && (b.width < minTouch - 0.5f || b.height < minTouch - 0.5f) &&
                !touchEnlarged(item.node, b, minTouch) && !coveredByClickableAncestor(item, infos, b, minTouch)) {
                add(if (m3) "small-target-m3" else "small-target", label, b)
            }
            if (item.text != null) {
                if (item.node.config.contains(SemanticsActions.GetTextLayoutResult)) {
                    checkText(item.node, label, b)
                } else if (m3) {
                    m3TextSkipped++
                } else {
                    add("audit-skip", "текст лише в семантиці, без GetTextLayoutResult — розрив/еліпсис не перевірено: $label", b)
                }
                checkLatin(item.text, b)
            }
        }
        if (m3TextSkipped > 0) {
            add("audit-skip", "M3 DatePicker: $m3TextSkipped клітинок — дата лише в семантиці (опис для TalkBack), " +
                "видима цифра без розмітки; перевірку тексту клітинок не виконано", rootBounds)
        }

        checkOverlaps(items, infos, scrollBoxes, rootArea)
    }

    /**
     * [scrollClip] — перетин меж скрол-предків: вузол, повністю за ним, прокручено з поля зору,
     * і його не перевіряємо (він не на знімку).
     */
    private fun walk(
        node: SemanticsNode, isRootNode: Boolean, rootBounds: Rect, scrollClip: Rect, ancestors: Set<Int>,
        scrolls: List<Int>, out: MutableList<Item>, infos: MutableMap<Int, Info>, scrollBoxes: MutableMap<Int, ScrollBox>,
    ) {
        // Нерозміщений вузол (prefetch/кеш LazyColumn/LazyGrid: скомпоновано й виміряно, але не
        // розміщено) не малюється і на знімку його немає; boundsInRoot у нього — позиція (0,0)
        // відносно батька, тож десятки таких «стоять» один на одному. Пропуск разом із піддеревом
        // — свідомий: це не пропущена перевірка, а вузол, якого на екрані не існує.
        if (!node.layoutInfo.isPlaced) return
        val b = node.boundsInRoot
        val config = node.config
        val hidden = config.contains(SemanticsProperties.InvisibleToUser)
        val clipped = if (scrollClip.overlaps(b)) b.intersect(scrollClip) else Rect.Zero
        val onScreen = !hidden && b.width > 0f && b.height > 0f && clipped.width > 0f && clipped.height > 0f
        val clickable = config.contains(SemanticsActions.OnClick)
        val vRange = config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        val hRange = config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
        infos[node.id] = Info(b, clickable, config.isMergingSemanticsOfDescendants, hRange != null)
        if (onScreen && !isRootNode) {
            out += Item(
                node = node,
                scrollVisible = clipped,
                visible = if (clipped.overlaps(rootBounds)) clipped.intersect(rootBounds) else Rect.Zero,
                ancestors = ancestors,
                scrolls = scrolls,
                clickable = clickable,
                text = textOf(node),
            )
        }
        val isScroll = vRange != null || hRange != null
        val childClip = if (isScroll) clipped else scrollClip
        if (isScroll) scrollBoxes[node.id] = scrollBox(clipped, vRange ?: hRange!!, vertical = vRange != null)
        val childScrolls = if (isScroll) scrolls + node.id else scrolls
        val childAncestors = ancestors + node.id
        for (child in node.children) {
            walk(child, isRootNode = false, rootBounds, childClip, childAncestors, childScrolls, out, infos, scrollBoxes)
        }
    }

    private fun scrollBox(visible: Rect, range: ScrollAxisRange, vertical: Boolean): ScrollBox {
        val value = range.value(); val maxValue = range.maxValue()
        val atStart = value <= 0.5f
        val atEnd = value >= maxValue - 0.5f
        // reverseScrolling (reverseLayout): value=0 — візуально нижній/правий край.
        return if (range.reverseScrolling) ScrollBox(visible, vertical, atStart = atEnd, atEnd = atStart)
        else ScrollBox(visible, vertical, atStart, atEnd)
    }

    /**
     * Перетини. Відсіюється (і чому це не дефект):
     *  - пара «текст + іконка/кнопка без тексту»: перевіряємо лише текст↔текст і клік↔клік;
     *  - перетин ≤20% меншого вузла: торкання країв, тіні, бейджі на іконці;
     *  - вузли зі спільним клікабельним / mergeDescendants предком (одна кнопка, рядок списку,
     *    чип, іконка з підписом у клікабельному батьку) — це один елемент для користувача;
     *    предок, що накриває ≥50% вікна (клікабельний фон екрана), групою НЕ вважається;
     *  - вузли ≥80% вікна (скрим, фон шторки) — їх напівпрозорість семантика не бачить,
     *    тому відсікаємо за розміром;
     *  - вміст скролу під нерухомим шаром там, куди його ще можна докрутити (рядки під шапкою
     *    після прокрутки вниз) — штатна поведінка скролу; недокручуване — `under-overlay`.
     */
    private fun checkOverlaps(items: List<Item>, infos: Map<Int, Info>, scrollBoxes: Map<Int, ScrollBox>, rootArea: Float) {
        val candidates = items.filter { (it.clickable || it.text != null) && it.visible.width > 0f &&
            it.visible.height > 0f && area(it.visible) < rootArea * 0.8f }
        for (i in candidates.indices) for (j in i + 1 until candidates.size) {
            val a = candidates[i]; val c = candidates[j]
            val bothText = a.text != null && c.text != null
            val bothClick = a.clickable && c.clickable
            if (!bothText && !bothClick) continue
            if (a.node.id in c.ancestors || c.node.id in a.ancestors) continue
            if (!a.visible.overlaps(c.visible)) continue
            val inter = a.visible.intersect(c.visible)
            if (inter.width <= 1f || inter.height <= 1f) continue
            if (area(inter) <= OVERLAP_SHARE * min(area(a.visible), area(c.visible))) continue
            if (shareGroup(a, c, infos, rootArea)) continue

            val label = "${labelOf(a.node)} | ${labelOf(c.node)}"
            val onlyA = a.scrolls.filter { it !in c.scrolls }
            val onlyC = c.scrolls.filter { it !in a.scrolls }
            if (onlyA.isEmpty() == onlyC.isEmpty()) {
                // Однаковий скрол-контекст (або два різні сусідні скроли) — рухаються разом.
                add("overlap", label, inter)
                continue
            }
            // Один у скролі S, другий — нерухомий шар над/під ним. S — зовнішній зі «своїх» скролів:
            // саме він зсуває вузол відносно іншого.
            val box = scrollBoxes[(onlyA.ifEmpty { onlyC }).first()] ?: continue
            if (unreachable(box, inter)) add("under-overlay", label, inter)
        }
    }

    /** Перетин у тому боці S, куди скрол уже не докрутить (або S взагалі не скролиться). */
    private fun unreachable(box: ScrollBox, inter: Rect): Boolean {
        val v = box.visible
        val towardEnd = if (box.vertical) inter.center.y > v.center.y else inter.center.x > v.center.x
        return if (towardEnd) box.atEnd else box.atStart
    }

    private fun shareGroup(a: Item, c: Item, infos: Map<Int, Info>, rootArea: Float): Boolean =
        a.ancestors.any { id ->
            id in c.ancestors && infos[id]?.let { (it.clickable || it.merging) && area(it.bounds) < rootArea * 0.5f } == true
        }

    /**
     * minimumInteractiveComponentSize (M3 IconButton/RadioButton/Checkbox/Switch/TextButton/Chip)
     * розширює LayoutNode до 48dp, а семантика clickable лишається на внутрішньому координаторі —
     * межі вузла менші, ніж реальна зона дотику. Ознака: на «малій» осі розмір LayoutNode рівно
     * 48dp (±1.5px). Рівно-48dp рядок з padding навколо дрібного clickable теж сюди потрапить —
     * це відомий компроміс.
     */
    private fun touchEnlarged(node: SemanticsNode, b: Rect, minTouch: Float): Boolean {
        val info = node.layoutInfo
        fun ok(bound: Float, layout: Int) = bound >= minTouch - 0.5f || (layout >= minTouch - 0.5f && layout <= minTouch + 1.5f)
        return ok(b.width, info.width) && ok(b.height, info.height)
    }

    /** Клікабельний предок ≥48dp, що повністю накриває вузол: тап поруч усе одно влучає в ціль. */
    private fun coveredByClickableAncestor(item: Item, infos: Map<Int, Info>, b: Rect, minTouch: Float): Boolean =
        item.ancestors.any { id ->
            val p = infos[id] ?: return@any false
            p.clickable && p.bounds.width >= minTouch - 0.5f && p.bounds.height >= minTouch - 0.5f &&
                p.bounds.left <= b.left + 1f && p.bounds.top <= b.top + 1f &&
                p.bounds.right >= b.right - 1f && p.bounds.bottom >= b.bottom - 1f
        }

    /**
     * Клітинка M3 DatePicker / DateRangePicker (день, рік): Role.Button, текст лише в семантиці
     * (опис дати; видима цифра — під clearAndSetSemantics), або квадрат 40dp у горизонтальному
     * пейджері місяців.
     */
    private fun isM3PickerCell(item: Item, infos: Map<Int, Info>, b: Rect, cell: Float): Boolean {
        val config = item.node.config
        if (config.getOrNull(SemanticsProperties.Role) != Role.Button) return false
        val semanticOnlyText = item.text != null && !config.contains(SemanticsActions.GetTextLayoutResult) &&
            !config.contains(SemanticsProperties.EditableText)
        if (semanticOnlyText) return true
        val square40 = abs(b.width - cell) <= 1.5f && abs(b.height - cell) <= 1.5f
        return square40 && item.ancestors.any { infos[it]?.horizontalScroll == true }
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
            add("audit-skip", "GetTextLayoutResult без дії: $label", b)
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
        if (!ellipsized) overflowReason(layout)?.let { add("overflow", "$label ($it)", b) }

        val text = layout.layoutInput.text.text
        for (word in WORD.findAll(text)) {
            if (word.value.length < 2) continue
            val first = layout.getLineForOffset(word.range.first)
            val last = layout.getLineForOffset(word.range.last)
            if (first != last) add("word-break", "«${word.value}» у «$label» (рядки $first–$last)", b)
        }
    }

    /**
     * Фактичне переповнення, а не прапорець hasVisualOverflow (він true і від шрифтового
     * padding / lineHeight на 1–2px, і на softWrap=false-полях, де текст вміщається).
     */
    private fun overflowReason(layout: TextLayoutResult): String? {
        if (layout.lineCount == 0) return null
        val width = layout.size.width.toFloat()
        var right = Float.NEGATIVE_INFINITY
        var left = Float.POSITIVE_INFINITY
        for (i in 0 until layout.lineCount) {
            right = max(right, layout.getLineRight(i))
            left = min(left, layout.getLineLeft(i))
        }
        if (right > width + 1f || left < -1f) {
            return String.format(Locale.ROOT, "ширина рядка %.0f..%.0f px при полі %.0f px", left, right, width)
        }
        if (layout.multiParagraph.didExceedMaxLines) return "обрізано по maxLines без еліпсиса"
        val last = layout.lineCount - 1
        val lineHeight = layout.getLineBottom(last) - layout.getLineTop(last)
        val cut = layout.getLineBottom(last) - layout.size.height
        if (lineHeight > 0f && cut > lineHeight * 0.5f) {
            return String.format(Locale.ROOT, "знизу відрізано %.0f з %.0f px рядка", cut, lineHeight)
        }
        return null
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
        val INFINITE = Rect(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        /** Частка площі меншого вузла, з якої перетин вважаємо суттєвим. */
        const val OVERLAP_SHARE = 0.2f
        /** Слово без дефіса: перенос після дефіса («IT-|послуги») розривом не вважаємо. */
        val WORD = Regex("""[\p{L}\p{N}'’]+""")
        val CURRENCY_CODE = Regex("""\b[A-Z]{3}\b""")
        /** Усе, крім літер: цифри, пробіли, пунктуація, символи валют. */
        val NON_LETTERS = Regex("""[^\p{L}]""")

        fun area(r: Rect) = r.width * r.height
    }
}
