<div align="center">

# ShieldCall - Know Who's Calling

**A community-powered Android caller ID, dialer and spam/scam call blocking app, built with Jetpack Compose**

![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20SDK-29-blue)
![GitHub release](https://img.shields.io/github/v/release/galaxyjammed/ShieldCall?logo=github&label=GitHub%20release)
![License](https://img.shields.io/badge/License-MIT-lightgrey?logo=mit)
![Project Status](https://img.shields.io/badge/Project%20Status-Finished-blue)

⭐ **If ShieldCall helps you, please star this repository. It helps other users find the project.**

</div>

## What is ShieldCall?

ShieldCall is a free, open-source Android app that tells you who is calling before you pick up. When a call comes in, a card shows whether the number is a saved contact, spam, or a scam. It can also be your full phone app: place calls, answer them, and manage multiple calls from one place. You can look up any number, see how other people rated it, and leave your own review. Decide what to block, and ShieldCall handles the rest.

<div align="center">
  <img width="100" height="100" alt="app_logo" src="https://github.com/user-attachments/assets/eafcdf2b-c13d-473b-bac3-0318bf0571ef" />
  <br>
  <em>"Your calls, no trouble"</em>
</div>

## Features

### 📞 Instant Caller Info
- A card appears as soon as a call comes in, showing the contact name, number and safety ranking
- Hints for unknown numbers, such as "Toll-free line, usually a company" or community labels like "Restaurant"
- Rate a number as **Safe**, **Spam** or **Scam** right after a call ends, without opening the app
- When ShieldCall isn't your default phone app, it shows a small card over your current screen. You can turn off the full-screen call option in Settings
- **Automatic call lookup** only applies when ShieldCall isn't your default phone app
- Warns you before calling back foreign numbers from one-ring scams and premium-rate numbers

### ☎️ Dialer and Calls
- A full dialer with a searchable keypad, contact suggestions as you type, and a redial key
- **Speed dial:** hold keys 1 to 9 to call a saved contact, set up from the speed dial editor
- **Multiple calls:** Decline, Hold & accept, End current call & accept, Swap and Merge
- **Dual SIM:** choose which SIM to call from, and see which SIM an incoming call arrived on
- **Audio routing** for phone speaker, wired headsets and Bluetooth devices
- **Decline with a reason:** send a preset or custom text to the caller when you can't pick up
- **Ongoing call notification** with End, Mute and Speaker, so you can leave the app mid-call
- The screen dims when the phone is near your face, so your cheek can't hang up or mute the call
- Emergency numbers work and skip every ShieldCall extra. Test them in Settings without dialing
- **Voicemail** from the dialer, and voicemail alerts with a Listen button
- **Silence the ring** as a blocking option, so the call goes to your history without ringing

### 🔔 Alerts and Reminders
- **Missed call alerts** with the caller's safety ranking, a time-of-ring note, and two buttons: **Call back** and **Call back in an hour**
- **Missed calls today** card on the Lookup screen, which opens your missed call history
- **Flash on call:** the torch blinks while the phone rings, so you notice it from across the room
- **Mute when face down:** a call mutes itself and the mic when the phone is lying face down
- **Birthdays:** add a birthday to a contact. A cake icon shows on their details on the day, and you get a reminder to call them
- **Post-call notes:** when an answered call ends, you can add a private note about it

### 🔎 Number Lookup
- Look up any number with a searchable country code picker, or paste one from the clipboard
- Numbers are validated for the selected country, so typos are caught before you search
- See the trust score, vote counts, reviews and the last few calls with that number
- **Share** a number's safety summary from the top bar
- **Private notes** on any number, visible only to you
- **Always allow** a number, so its calls get through whatever your blocking rules say
- Shortcuts for **Call, Message, Favorite, Block/Unblock** and **Edit** (for saved contacts)
- **Add to contacts** for unknown numbers in one tap

### ⭐ Community Votes and Reviews
- Vote a number **Safe**, **Spam** or **Scam**, and optionally tag the kind of call or place
- **Change or remove your vote** at any time
- One vote per Google account per number, so one person can't flood a number with votes
- Write a short review, and post it under your name or anonymously
- Like helpful reviews, sort by **Newest** or **Most liked**, and load more
- **Report** reviews that look unfair or abusive. Reviews reported by several people are hidden
- **Report a wrong label** when a number's label looks incorrect. Moderators see these reports
- **Contributor badges** (Bronze, Silver, Gold) based on how many numbers you've voted on. They show on your profile and next to your reviews

### 👥 Contacts, Favorites and History
- Browse your contacts with photos, and tap any contact to see their details
- Merged duplicate entries, so the same number saved in different formats shows as one contact
- **Favorites strip** at the top of Contacts for one-tap calls
- **Add contacts** and **edit contacts** (name, numbers, emails, birthday and call background) right in the app. Changes sync with your Google account
- **Call history** with incoming, outgoing, missed, declined, silenced and blocked calls, grouped repeats, filters, and swipe to call or delete
- Block or unblock any contact or number with one tap

### 🛡️ Smart Blocking
- Block calls from **Spam & scam**, **Businesses**, **Other countries**, **Unknown numbers**, **Unsaved numbers**, or numbers starting with a **prefix**
- When a call matches, choose to **hang up**, **block** the number, or **silence** it
- **Quiet hours:** during set hours, calls from unsaved numbers are handled by your chosen action
- **Blocklist manager** for numbers, names, countries and prefixes
- **Always allowed** list for numbers that should never be blocked
- **Blocked calls log** with an Unblock button for numbers on your list

### 📊 Stats
- How many calls you've identified as Spam, Scam and Safe, with a tap to see the numbers and change their votes
- Daily, weekly and monthly activity graphs
- Your activity: votes, reviews and lookups
- Estimated time saved by hanging up on blocked calls
- Blocked call insights: your busiest time, where calls come from, and repeat callers

### 🔒 PIN / Fingerprint Lock
- Protect the app with a **PIN** or your **fingerprint**
- Keeps your lookups, blocked numbers and call history private if someone else picks up your phone

### ⚙️ Settings and Themes
- **Light and dark** themes
- **Call screen background** for all calls, plus a different background for any contact
- **Backup and restore** of your blocklist, favorites and settings
- **Missing permissions card** that shows what's still needed and takes you straight to each setting
- **Contact us** for bug reports and feedback, with device details filled in
- **What's new** after each update, and a version card with links to the privacy policy and licenses
- Update prompts that let you download the new version straight from GitHub

### 📦 Widgets
- **Shortcut widget:** Lookup, Dial, Recent calls and Contacts in one tap
- **Stats widget:** Identified calls (Spam, Scam, Safe) and your activity (Votes, Reviews, Lookups)
- **Favorites widget:** Let's you add up-to 6 contacts you deem most important so you can easily call them through the home screen
- All widgets follow your light and dark theme, and show previews before you add them

### 🚀 More to Come
- ShieldCall is a work in progress and new features are on the way.

## 🔐 Privacy

- Your contacts, blocklist, favorites, private notes, call history, speed dial and backgrounds stay **on your device**
- ShieldCall keeps its own short history of the calls it handles. It doesn't read your system call log
- Birthdays are saved in your phone's contacts, so they sync the same way as any other contact field
- When you vote, review or report, the number, your vote, and your review are stored online so other users can see them. Signing in with Google is required for this. Your account ID is stored as an anonymous identifier, and it's never shown to other users
- ShieldCall has no ads and does not sell your data
- You can delete all of your online data at any time from **Settings → Delete Account & Data**
- Crash reports are sent to Firebase Crashlytics, and you can turn them off in **Settings → Privacy**
- Emergency calls (112, 911 and similar) work normally, whatever features are turned on

## 🛠️ Setup

1. Download the APK from [Releases](../../releases)
2. Install it and open ShieldCall
3. Grant the permissions the welcome screen asks for:
   - Contacts and phone state
   - Display over other apps
   - Set ShieldCall as your **Caller ID & spam app**
   - Set ShieldCall as your **default phone app** if you want the full dialer and call screen
4. Look up a number, or wait for the next call

The **Settings** screen shows any permission that is still missing, and takes you straight to it.

Simple and easy!

## ❓ Frequently Asked Questions

### Is ShieldCall free?
Yes. ShieldCall is free and open-source, with no ads and no subscriptions.

### Why does it need so many permissions?
Contacts let it recognise people you know. Display over other apps lets it show the caller card. The caller ID and phone app roles are how Android lets an app see incoming calls and place its own. Notifications power the missed call and reminder alerts.

### Do I need to be the default phone app?
No. As a caller ID and spam app, ShieldCall still identifies and blocks calls. Being the default phone app adds the dialer, the call screen, multiple calls, speed dial and the other calling features.

### Does ShieldCall work offline?
Mostly. Contacts, blocking, call history, your notes, speed dial, birthdays and the missed call strip all work offline. Voting, reviews and community results need an internet connection.

### Why do I need to sign in with Google to vote?
Signing in keeps votes honest by allowing one vote per person on each number. Looking up numbers and receiving call info doesn't require signing in.

### How does it decide a number is a business?
Phone numbers don't say whether they belong to a business, so ShieldCall treats toll-free, premium-rate, shared-cost and VoIP numbers as business numbers. It's a best guess and may not be perfect.

### Can ShieldCall block calls on its own?
Yes. When a call matches your rules or your blocklist, ShieldCall rejects, silences or hangs up on it automatically. This works as long as ShieldCall stays your Caller ID & spam app.

### Can I test the call screen without a real call?
Yes. Go to **Settings → Dial → Test call with fake name**. You can add a fake caller, a number, and looping audio that plays as if the caller is talking.

### Will my calls still work if ShieldCall has a problem?
Emergency calls always go through. If you're ever unsure, you can switch your default phone app back in Android's settings.

### What is the minimum Android version?
ShieldCall requires Android 10 (API level 29) or higher.

## Bug Reports/Suggestions
- Feel free to report bugs or give suggestions and feedback by opening an [issue](https://github.com/galaxyjammed/ShieldCall/issues) or [discussion](https://github.com/galaxyjammed/ShieldCall/discussions) thread!
- You can also send bug reports or feedback in the app: **Settings → About → Contact us**

## 📄 License

The source code is MIT licensed. Firebase and Google services are subject to their own terms. Community votes, reviews and reports are contributed by users and are not covered by the MIT license.
See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for how data is handled and [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for the libraries used.
