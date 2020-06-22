package javadoc.pages

import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.SourceSetData
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.pages.*

enum class JavadocContentKind : Kind {
    AllClasses, OverviewSummary, PackageSummary, Class, OverviewTree, PackageTree
}

abstract class JavadocContentNode(
    dri: Set<DRI>,
    kind: Kind,
    override val sourceSets: Set<SourceSetData>
) : ContentNode {
    override val dci: DCI = DCI(dri, kind)
    override val style: Set<Style> = emptySet()
    override val extra: PropertyContainer<ContentNode> = PropertyContainer.empty()
    override fun withNewExtras(newExtras: PropertyContainer<ContentNode>): ContentNode = this
}

interface JavadocListEntry {
    val stringTag: String
}

class EmptyNode(
    dri: DRI,
    kind: Kind,
    override val sourceSets: Set<SourceSetData>,
    override val extra: PropertyContainer<ContentNode> = PropertyContainer.empty()
) : ContentNode {
    override val dci: DCI = DCI(setOf(dri), kind)
    override val style: Set<Style> = emptySet()

    override fun withNewExtras(newExtras: PropertyContainer<ContentNode>): ContentNode =
        EmptyNode(dci.dri.first(), dci.kind, sourceSets, newExtras)

    override fun hasAnyContent(): Boolean = false
}

class JavadocContentGroup(
    val dri: Set<DRI>,
    val kind: Kind,
    sourceSets: Set<SourceSetData>,
    val children: List<JavadocContentNode>
) : JavadocContentNode(dri, kind, sourceSets) {

    companion object {
        operator fun invoke(
            dri: Set<DRI>,
            kind: Kind,
            sourceSets: Set<SourceSetData>,
            block: JavaContentGroupBuilder.() -> Unit
        ): JavadocContentGroup =
            JavadocContentGroup(dri, kind, sourceSets, JavaContentGroupBuilder(sourceSets).apply(block).list)
    }

    override fun hasAnyContent(): Boolean = children.isNotEmpty()
}

class JavaContentGroupBuilder(val sourceSets: Set<SourceSetData>) {
    val list = mutableListOf<JavadocContentNode>()
}

class TitleNode(
    val title: String,
    val subtitle: List<ContentNode>,
    val version: String,
    val parent: String?,
    val dri: Set<DRI>,
    val kind: Kind,
    sourceSets: Set<SourceSetData>
) : JavadocContentNode(dri, kind, sourceSets) {
    override fun hasAnyContent(): Boolean = !title.isBlank() || !version.isBlank() || subtitle.isNotEmpty()
}

fun JavaContentGroupBuilder.title(
    title: String,
    subtitle: List<ContentNode>,
    version: String,
    parent: String? = null,
    dri: Set<DRI>,
    kind: Kind
) {
    list.add(TitleNode(title, subtitle, version, parent, dri, kind, sourceSets))
}

data class TextNode(
    val text: String,
    override val sourceSets: Set<SourceSetData>
) : JavadocContentNode(emptySet(), ContentKind.Main, sourceSets) {
    override fun hasAnyContent(): Boolean = !text.isBlank()
}

class ListNode(
    val tabTitle: String,
    val colTitle: String,
    val children: List<JavadocListEntry>,
    val dri: Set<DRI>,
    val kind: Kind,
    sourceSets: Set<SourceSetData>
) : JavadocContentNode(dri, kind, sourceSets) {
    override fun hasAnyContent(): Boolean = children.isNotEmpty()
}

fun JavaContentGroupBuilder.list(
    tabTitle: String,
    colTitle: String,
    dri: Set<DRI>,
    kind: Kind,
    children: List<JavadocListEntry>
) {
    list.add(ListNode(tabTitle, colTitle, children, dri, kind, sourceSets))
}


class LinkJavadocListEntry(
    val name: String,
    val dri: Set<DRI>,
    val kind: Kind = ContentKind.Symbol,
    val sourceSets: Set<SourceSetData>
) :
    JavadocListEntry {
    override val stringTag: String
        get() = if (builtString == null)
            throw IllegalStateException("stringTag for LinkJavadocListEntry accessed before build() call")
        else builtString!!

    private var builtString: String? = null

    fun build(body: (String, Set<DRI>, Kind, List<SourceSetData>) -> String) {
        builtString = body(name, dri, kind, sourceSets.toList())
    }
}

data class RowJavadocListEntry(val link: LinkJavadocListEntry, val doc: List<ContentNode>) : JavadocListEntry {
    override val stringTag: String = ""
}