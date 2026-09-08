package com.autoinsta.ui.coach

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoinsta.domain.CaptionCoach
import com.autoinsta.domain.CaptionDraft
import com.autoinsta.domain.CoachAnswers
import com.autoinsta.domain.CoachSuggestions
import com.autoinsta.domain.PublishPolicy
import com.autoinsta.domain.TagSuggestion
import com.autoinsta.domain.TitleSuggestion

/**
 * The caption coach, as a screen that takes over while it is open.
 *
 * ## The shape of this screen is the point
 * It opens on **two questions**, not on suggestions. Nothing it produces is applied until
 * something is ticked, and every suggestion is labelled with the move it used — which of
 * the five title sources, which hashtag role, which of the caption's three parts. The
 * owner asked to *learn to write these themselves*; a screen that filled the fields in
 * would quietly do the opposite.
 *
 * Nothing here decides anything. The rules are in [CaptionCoach] and the merging is in
 * `ComposePostViewModel.applyCoach` — both testable without a device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionCoachScreen(
    stage: CoachStage,
    onAnswersChange: (CoachAnswers) -> Unit,
    onRequest: () -> Unit,
    onApply: (String?, CaptionDraft?, List<String>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = stage as? CoachStage.Ready

    // Keyed on the suggestions so a fresh set starts unticked — nothing is ever pre-chosen.
    var chosenTitle by remember(ready?.suggestions) { mutableStateOf<String?>(null) }
    var useCaption by remember(ready?.suggestions) { mutableStateOf(false) }
    var chosenTags by remember(ready?.suggestions) { mutableStateOf(emptySet<String>()) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Caption coach") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close the coach")
                    }
                },
            )
        },
        bottomBar = {
            if (ready != null) {
                ApplyBar(
                    chosenCount = countChosen(chosenTitle, useCaption, chosenTags),
                    onApply = {
                        onApply(
                            chosenTitle,
                            if (useCaption) ready.suggestions.caption else null,
                            chosenTags.toList(),
                        )
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (stage) {
                is CoachStage.Asking -> AskingStage(
                    answers = stage.answers,
                    onAnswersChange = onAnswersChange,
                    onRequest = onRequest,
                )

                CoachStage.Thinking -> ThinkingStage()

                is CoachStage.Failed -> FailedStage(
                    reason = stage.reason,
                    onRetry = onRequest,
                    onClose = onClose,
                )

                is CoachStage.Ready -> ReadyStage(
                    suggestions = stage.suggestions,
                    chosenTitle = chosenTitle,
                    onTitleClick = { chosenTitle = if (chosenTitle == it) null else it },
                    useCaption = useCaption,
                    onUseCaptionChange = { useCaption = it },
                    chosenTags = chosenTags,
                    onTagToggle = { tag ->
                        chosenTags = if (tag in chosenTags) chosenTags - tag else chosenTags + tag
                    },
                )

                CoachStage.Closed -> Unit
            }
        }
    }
}

/**
 * The two questions, asked before anything is suggested.
 *
 * Both are optional on purpose: some days there is nothing to say yet, and a coach that
 * refuses to help until you have written something is a coach you stop opening. Skipping
 * is a labelled path, not a trick — the request says so, and the answer comes back more
 * tentative.
 */
@Composable
private fun AskingStage(
    answers: CoachAnswers,
    onAnswersChange: (CoachAnswers) -> Unit,
    onRequest: () -> Unit,
) {
    Text(
        text = "Your words first",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = "These go into the request ahead of everything else, and the suggestions get " +
            "built on top of them. Two lines is plenty.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Column {
        OutlinedTextField(
            value = answers.whatWasHard,
            onValueChange = { onAnswersChange(answers.copy(whatWasHard = it)) },
            label = { Text("What was hard about this one?") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Hint("The bit you nearly gave up on. This becomes the line nobody else could write.")
    }

    Column {
        OutlinedTextField(
            value = answers.whatItsAbout,
            onValueChange = { onAnswersChange(answers.copy(whatItsAbout = it)) },
            label = { Text("What's it about, in three words?") },
            minLines = 1,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Hint("Not what's in the picture — what it's about. \"Leaving somewhere.\" \"Too quiet.\"")
    }

    Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
        Text("Get suggestions")
    }
    if (!answers.hasSomething) {
        TextButton(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Text("Skip — work from the picture alone")
        }
    }
}

@Composable
private fun ThinkingStage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = "Looking at your piece…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun FailedStage(
    reason: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Text(
        text = reason,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
    )
    Text(
        text = "Your answers are still here — trying again won't make you type them out.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
        Text("Write it myself")
    }
}

@Composable
private fun ReadyStage(
    suggestions: CoachSuggestions,
    chosenTitle: String?,
    onTitleClick: (String) -> Unit,
    useCaption: Boolean,
    onUseCaptionChange: (Boolean) -> Unit,
    chosenTags: Set<String>,
    onTagToggle: (String) -> Unit,
) {
    SectionHeader(
        title = "${CaptionCoach.TITLE_COUNT} ways to name it",
        blurb = "Each uses a different move, named underneath. Read all three before you " +
            "pick — or use them as a run-up to your own.",
    )
    suggestions.titles.forEach { title ->
        TitleCard(
            title = title,
            chosen = chosenTitle == title.text,
            onClick = { onTitleClick(title.text) },
        )
    }

    SectionHeader(
        title = "A caption in three parts",
        blurb = "The shape is worth more than the words: something that isn't about the " +
            "art, one real detail from making it, then something easy to answer.",
    )
    CaptionCard(
        draft = suggestions.caption,
        chosen = useCaption,
        onChange = onUseCaptionChange,
    )

    SectionHeader(
        title = "${PublishPolicy.MAX_HASHTAGS} hashtags",
        blurb = "Instagram allows ${PublishPolicy.MAX_HASHTAGS} now, and they classify " +
            "rather than reach. Tick the ones that are true of this piece.",
    )
    suggestions.hashtags.forEach { tag ->
        TagRow(
            tag = tag,
            chosen = tag.tag in chosenTags,
            onToggle = { onTagToggle(tag.tag) },
        )
    }
}

@Composable
private fun SectionHeader(title: String, blurb: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TitleCard(
    title: TitleSuggestion,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (chosen) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        border = if (chosen) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title.text, style = MaterialTheme.typography.titleMedium)
            Box(modifier = Modifier.padding(top = 8.dp)) {
                Label(title.source)
            }
            Text(
                text = title.why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * The draft, shown in its three labelled parts rather than as one block.
 *
 * Seeing the seams is what makes the pattern reusable next time without opening this at all.
 */
@Composable
private fun CaptionCard(
    draft: CaptionDraft,
    chosen: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val parts = listOf(
        Triple("Hook", "not about the art", draft.hook),
        Triple("Process", "only you could write this", draft.process),
        Triple("Invitation", "answerable in three words", draft.invitation),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (chosen) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        border = if (chosen) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            parts.forEachIndexed { index, (name, job, text) ->
                if (text.isNotBlank()) {
                    Text(
                        text = "${name.uppercase()} — $job",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 14.dp),
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable { onChange(!chosen) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = chosen, onCheckedChange = onChange)
                Text("Use this caption", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TagRow(
    tag: TagSuggestion,
    chosen: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = chosen, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.padding(start = 4.dp, top = 12.dp)) {
            Text(tag.tag, style = MaterialTheme.typography.bodyLarge)
            Box(modifier = Modifier.padding(top = 4.dp)) {
                Label(tag.role)
            }
            if (tag.note.isNotBlank()) {
                Text(
                    text = tag.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** The small tag that names the move a suggestion used. */
@Composable
private fun Label(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}

/**
 * Nothing is applied until something is ticked, and the button says how much.
 *
 * "Write it myself" is given equal weight rather than hidden as a back gesture — leaving
 * with nothing has to stay an obvious, respectable choice.
 */
@Composable
private fun ApplyBar(
    chosenCount: Int,
    onApply: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = onApply,
                enabled = chosenCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (chosenCount == 0) {
                        "Tick what you want to keep"
                    } else {
                        "Add $chosenCount to the post"
                    }
                )
            }
            Text(
                text = "Whatever you've already typed is kept — this only adds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

private fun countChosen(title: String?, useCaption: Boolean, tags: Set<String>): Int =
    (if (title != null) 1 else 0) + (if (useCaption) 1 else 0) + tags.size
