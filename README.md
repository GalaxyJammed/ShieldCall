<div align="center">

# ShieldCall - Know Who's Calling

**A community-powered Android caller ID and spam protection app, built with Jetpack Compose**

![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20SDK-29-blue)
![GitHub release](https://img.shields.io/github/v/release/galaxyjammed/ShieldCall?logo=github&label=GitHub%20release)
![License](https://img.shields.io/badge/License-MIT-lightgrey?logo=mit)
![Project Status](https://img.shields.io/badge/Project%20Status-Early_WIP-blue)

⭐ **If ShieldCall helps you, please star this repository. It helps other users find the project.**

</div>

## What is ShieldCall?

ShieldCall is a free, open-source Android app that tells you who is calling before you pick up. When a call comes in, a card appears on your screen showing whether the number is a saved contact, spam, or a scam. You can also look up any number yourself, see how other people rated it, and leave your own review. Decide what to block, and ShieldCall hangs up for you.

<div align="center">
  <img width="100" height="100" alt="app_logo" src="https://github.com/user-attachments/assets/f49c5dbe-6230-40e2-8e35-fbf3d8438b87" />
  <br>
  <em>"Your calls, no trouble"</em>
</div>

## Features

### 📞 Instant Caller Info
- A card appears as soon as a call comes in, showing the contact name, contact number, safety ranking and a way to immediately mark as **SAFE/SPAM/SCAM** yourself
- Works through Android's call screening, so the info shows up while the phone is still ringing
- Can be switched off at any time with the **Automatic call lookup** setting (disabled when having full-screen calls enabled)
- **Decline & Message** or send **Voicemails** through the dialer incase you wish to respond without calling
- Notifications on missed calls that show the "Safety Ranking" of a missed call along with a "Call Back" button

### 🔎 Number Lookup
- Look up any phone number with a searchable **country code picker**
- Numbers are validated for the selected country, so typos and impossible numbers are caught before you search
- See the number's **trust score** and how many people voted it Safe, Spam or Scam along with reviews

### ⭐ Community Votes and Reviews
- Vote a number as **Safe**, **Spam** or **Scam** and add a short written review about your experience
- One vote per Google account per number, so a single person can't flood a number with reviews
- Reviews are only loaded when you tap **Show reviews**, which keeps the app fast and light
- Vote anonymously or using your Google Account
- Like other reviews and filter by **Newest/Most Liked** reviews for ease or report ones you think were done in bad faith

### 👥 Contacts and Recent Calls
- Browse your contacts with their photos and tap anyone to see their safety screen
- Switch to **Recent Calls** with the filter button to see the last 100 calls ShieldCall screened along with information
- Block or unblock any contact or number with one tap

### 🛡️ Smart Blocking Preferences
- Block calls from **Spam & scam**, **Businesses**, **Other countries**, **Unknown numbers** or **Unsaved numbers**
- Choose what happens when a call matches:
  - **Hang up immediately**
  - **Block the number** and add it to your blocked list
- Saved contacts are never caught by the category rules
- Unblock any contact whenever you want in the click of a button

### 📊 Stats
- Shows how many calls you identified as Spam,Scam,Safe
- A visual daily/weekly/monthly graph
- How many and which countries looked/searched for your number inside of the app
- How many seconds you saved from instantly hanging up on spam/scam calls

### 🔒 PIN / Fingerprint Lock
- Protect the app with a **PIN** or your **fingerprint**
- Keeps your lookups, blocked numbers and call history private if someone else picks up your phone

### ⚙️ Settings and Themes
- Switch between **light** and **dark** themes
- Smooth slide animations between screens and a bottom bar that highlights the screen you're on

### 🚀 More to Come
- ShieldCall is a work in progress and new features are on the way.

## 🔐 Privacy

- Your contacts and blocked numbers stay **on your device**
- ShieldCall only keeps its own short history of the calls it screens, and doesn't read your system call log
- When you vote or review, the number, your vote and your review are stored online so other users can see them. You must sign in with Google for this, and your account ID is stored as an anonymous identifier, never shown to anyone
- ShieldCall has no ads and does not sell your data
- You can delete all data at any given moment when you sign in with your Google Account
- Emergency calls (112,911 etc) work and ignore every redundant feature in the Dialer (can be tested in Settings)

## 🛠️ Setup

1. Download the APK from [Releases](../../releases)
2. Install it and open ShieldCall
3. Grant the three permissions the welcome screen asks for:
   - Contacts and phone state
   - Display over other apps
   - Set ShieldCall as your default **Caller ID & spam app** as well as **Dial app**
4. Look up a number, or wait for the next call (make sure **Automatic call lookup** is enabled)

Simple and easy!

## ❓ Frequently Asked Questions

### Is ShieldCall free?
Yes. ShieldCall is completely free and open-source, with no ads and no subscriptions.

### Why does it need so many permissions?
Contacts let it recognise people you know, the overlay permission lets it show the info card over your call screen, and the caller ID role is how Android lets an app see incoming numbers.

### Does ShieldCall work offline?
Mostly. Contacts, blocking, your cached numbers and the built-in list all work offline. Voting, reviews and live community results need an internet connection.

### Why do I need to sign in with Google to vote?
Signing in keeps votes honest by allowing one vote per person on each number. Looking up numbers and receiving call info doesn't require signing in.

### How does it decide a number is a business?
Phone numbers don't say whether they belong to a business, so ShieldCall treats toll-free, premium-rate, shared-cost and VoIP numbers as business numbers. It's a best guess and may not be perfect.

### Can ShieldCall block calls on its own?
Yes. When a call matches your rules or your blocked list, ShieldCall rejects it automatically. This works as long as it stays set as your Caller ID & spam app.

### What is the minimum Android version?
ShieldCall requires Android 10 (API level 29) or higher.

## Bug Reports/Suggestions
- Feel free to report bugs or suggestions by opening an [issue](https://github.com/galaxyjammed/ShieldCall/issues) or [discussion](https://github.com/galaxyjammed/ShieldCall/discussions) thread!

## 📄 License

The source code is MIT licensed. The bundled number list and third-party services (Firebase) are subject to their own terms.
