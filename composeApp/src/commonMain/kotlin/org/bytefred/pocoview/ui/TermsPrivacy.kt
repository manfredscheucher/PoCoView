package org.bytefred.pocoview.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * The website's Terms & privacy note (About dialog, web build only, collapsed until clicked). Word for word the approved
 * "Draft" of misc/turingmachine/doc/privacy-github-pages.md (Manfred, 2026-11-24), the same in
 * ArrView, PoCoView and RotSysView. "privacy statement" and "please let me know" are links.
 */
@Composable
fun TermsAndPrivacy(feedbackUrl: String) {
    Spacer(Modifier.height(8.dp))
    // collapsed by default, like the license texts
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = !show }) { Text(if (show) "Hide terms & privacy" else "Terms & privacy") }
    if (!show) return
    Text("Privacy", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    for (p in PRIVACY_PARAGRAPHS) LinkParagraph(p, listOf(PRIVACY_LINK_LABEL to GITHUB_PRIVACY_URL))
    Text("Terms", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    LinkParagraph(TERMS_PARAGRAPH, listOf(FEEDBACK_LINK_LABEL to feedbackUrl))
}

/** [text] as bodySmall with each label of [links] (label to url) underlined and clickable. */
@Composable
private fun LinkParagraph(text: String, links: List<Pair<String, String>>) {
    val uri = LocalUriHandler.current
    val linkStyles = TextLinkStyles(SpanStyle(color = Color(0xFF1A4FB0), textDecoration = TextDecoration.Underline))
    val annotated = buildAnnotatedString {
        append(text)
        for ((label, url) in links) {
            val i = text.indexOf(label)
            if (i >= 0) addLink(LinkAnnotation.Clickable(url, linkStyles) { openUriSafely(uri, url) }, i, i + label.length)
        }
    }
    Text(annotated, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
}

internal const val GITHUB_PRIVACY_URL = "https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement"
internal const val PRIVACY_LINK_LABEL = "privacy statement"
internal const val FEEDBACK_LINK_LABEL = "please let me know"

internal val PRIVACY_PARAGRAPHS = listOf(
    "I don't collect anything. This site has no server of its own, no ads, no analytics and no trackers, " +
        "and it sets no cookies. Your browser downloads the scripts, and then everything runs locally " +
        "on your machine. Whatever you do on this site stays on your computer.",
    "Your preferred settings are kept in your browser's local storage, so they're still there next time. " +
        "They never leave your computer. Clearing your browser data removes them.",
    "The app is built with Compose Multiplatform. Compose would like to fetch extra fonts from Google when you " +
        "type characters its built-in fonts don't know (emoji, Chinese, ...). That's switched off here: everything " +
        "this page loads comes from GitHub Pages, so such characters just show up as boxes.",
    "The site is hosted on GitHub Pages. Like most web hosts, GitHub keeps server logs that include visitors' " +
        "IP addresses, for security purposes; details are in GitHub's privacy statement. I don't have access to " +
        "these logs. You might also download the source and run it locally.",
)

internal const val TERMS_PARAGRAPH = "There is no guarantee of correctness or availability. " +
    "I'll do my best to keep this site running and to fix problems. If you experience problems or have general " +
    "feedback or ideas, please let me know!"
