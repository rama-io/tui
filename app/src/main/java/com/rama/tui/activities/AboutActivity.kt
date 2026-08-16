package com.rama.tui.activities

import com.rama.bohio.activity.BohioAboutActivity
import com.rama.tui.R

class AboutActivity : BohioAboutActivity() {
    override val appIconRes = R.drawable.tui
    override val appDescriptionRes = R.string.app_desc
    override val appNameRes = R.string.app_name
    override val appClaimsArrayRes = R.array.app_claims
}
