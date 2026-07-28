package dev.antonlammers.trainist.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.BuildConfig
import dev.antonlammers.trainist.R

/**
 * The three ways to support Trainist — rate it, donate, or write to the developer.
 *
 * All live at the end of the Help Center (`ui/help/HelpScreen`): a question the FAQ doesn't answer
 * should lead straight to a mail draft, so support isn't a destination of its own. [DonateButton]
 * and the rating link are deliberately *also* reachable from the settings hub itself: a donation
 * (or a rating) the user has to go looking for is one that doesn't happen.
 */

/** PayPal donation — used both in the Help Center and directly on the settings hub. */
@Composable
fun DonateButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Button(
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONATION_URL))) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(
            Icons.Rounded.VolunteerActivism,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(stringResource(R.string.settings_donation_cta))
    }
}

/**
 * Opens a prefilled mail draft. Subject and body both name what is being asked for — feedback and
 * concrete suggestions — so an empty compose window doesn't have to be filled from a blank page;
 * the body also carries [BuildConfig.VERSION_NAME] so a report arrives with its version attached.
 */
@Composable
fun FeedbackButton() {
    val context = LocalContext.current
    // Resolved here (not inside the onClick lambda below, which isn't a @Composable context).
    val subject = stringResource(R.string.settings_contact_email_subject)
    val body = stringResource(R.string.settings_contact_email_body, BuildConfig.VERSION_NAME)

    OutlinedButton(
        onClick = {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEVELOPER_CONTACT_EMAIL")).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(
            Icons.Rounded.MailOutline,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(stringResource(R.string.settings_contact_developer_button))
    }
}

/** Opens Trainist's Play Store listing, where the user can leave a rating. */
@Composable
fun RateButton() {
    val context = LocalContext.current
    OutlinedButton(
        onClick = { context.openPlayStoreListing() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(
            Icons.Rounded.StarRate,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(stringResource(R.string.settings_rate_button))
    }
}

/**
 * Sends the user to the Play Store listing for [BuildConfig.APPLICATION_ID].
 *
 * A plain deep link, deliberately not Play's in-app review API: Google's guidance forbids putting
 * that flow behind a button (it is meant to fire at a natural moment, and it silently does nothing
 * for installs that didn't come from Play), so a button promising a rating dialog would often
 * deliver none. `market://` opens the Play app directly; the https form covers devices without it.
 * If neither resolves there is nothing left to open, so the tap does nothing rather than crash.
 */
internal fun Context.openPlayStoreListing() {
    val listings = listOf(
        "market://details?id=${BuildConfig.APPLICATION_ID}",
        "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}",
    )
    for (listing in listings) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(listing)))
            return
        } catch (_: ActivityNotFoundException) {
            // No handler for this form — fall through to the next one.
        }
    }
}

/** Support inbox for feedback, bugs and feature suggestions. */
private const val DEVELOPER_CONTACT_EMAIL = "lammy.google.develop.flatness494@passmail.net"

private const val DONATION_URL = "https://paypal.me/antonlamm"
