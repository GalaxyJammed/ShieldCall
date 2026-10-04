# ShieldCall Privacy Policy

_Last updated: October 3, 2026_

ShieldCall is a free, open-source Android app that identifies callers, lets people rate phone numbers, and blocks unwanted calls. This page explains what data the app handles and why.

## What stays on your device

These never leave your phone:

- **Your contacts.** ShieldCall reads them to show names and photos and to recognise saved numbers. They are not uploaded.
- **Your blocklist, favorites and settings**, including the optional PIN and fingerprint lock.
- **Call history.** ShieldCall keeps its own short history of calls it handles (number, time, duration, and whether the call was incoming, outgoing, missed, declined or blocked). It does not read the system call log.
- **Recent lookups** and backups you export yourself.

Call audio is never recorded, and the content of text replies is never stored by ShieldCall.

## What is sent to the community database (Firebase)

When you sign in with Google and look up a number, vote on it, or write a review, ShieldCall uses Google Firebase (Firestore and Authentication) to:

- **Look up community ratings** for a number. The number (country code plus national number) is sent to retrieve its vote counts and reviews. This also happens when a call comes in, if you are signed in.
- **Store your votes and reviews.** A vote or review is stored with your Firebase account ID. A review also stores the name shown with it: your Google account name, or "Anonymous User" if you choose to stay anonymous in Settings. Other users can see votes counts, reviews, and the name shown on a review. They cannot see your email.
- **Keep your account data.** Your Firebase user record stores your Google account ID, email, display name and profile photo link, a list of the numbers you identified.

Votes and reviews are public to other signed-in users of the app.

## Sign-in

ShieldCall uses Google Sign-In through Firebase Authentication. Signing in is only required to see community ratings, vote and review. Looking up call information from your own contacts and blocking work without it.

## Crash reports

ShieldCall uses Firebase Crashlytics to receive crash reports. A report contains technical data such as the error, app version, Android version, device model and an installation identifier. It does not contain your contacts, numbers or call history. You can switch this off at any time in **Settings → Privacy → Send crash reports**.

## Update checks

At launch the app contacts the GitHub API to find the latest ShieldCall release. GitHub can see your IP address in that request, as with any web request.

## Permissions

| Permission | Why |
| --- | --- |
| Contacts | Recognise saved numbers, show names and photos |
| Phone / call handling | Screen, answer, reject and place calls |
| Display over other apps | Show the small caller info card |
| Notifications | Missed call and voicemail alerts |
| Microphone, network | Used by Android's calling system and for community lookups |

## Deleting your data

In **Settings → Delete Account & Data**, ShieldCall removes your votes, reviews, likes and Firebase user record, clears its local lookup data, and deletes your Firebase account. A report you filed against someone else's review may leave an anonymous marker that cannot be linked back to you after your account is gone. If the app cannot delete the account itself (for example when your sign-in is old), your data is still deleted and you can sign in again and repeat it to remove the account.

You can also open an issue on this repository to ask for help with deletion.

## Sharing

ShieldCall does not sell your data and has no ads. Data is processed by Google (Firebase) and GitHub as described above, under their own privacy policies.

## Children

ShieldCall is not designed for children under 13.

## Changes

If this policy changes, the date above is updated. Changes to the app that affect privacy are listed in the release notes.

## Contact

Questions or requests: open an issue at https://github.com/galaxyjammed/ShieldCall/issues
