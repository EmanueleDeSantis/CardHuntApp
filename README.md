# CardHunt

CardHunt is a location-based mobile game in which players explore real-world places to collect virtual cards. Cards are real — the actual photo capture of a target real-world place. Staff members, which are moderators together with the admin, place cards at GPS coordinates with a collection radius; when a player enters the radius, they can capture the card by taking a photo with the phone camera. The photo is stylized according to the card's rarity (Common, Rare, Epic, Legendary), stored in the player's collection, and awards XP and achievements.

## Functionalities

**Map exploration**
OpenStreetMap view with colour-coded markers (red = not collected, violet = received as a share, green = collected with my own photo), walking directions and a compass arrow pointing to the selected card.

**Card collection**
Camera capture with a confirm/retake step, GPS proximity verification, and server-side anti-cheat validation that rejects fake photos (fully black, fully white or solid-colour images).

**Social**
User search and friend requests, card sharing (only original shoots can be shared; a shared card can later be upgraded to an original shoot by taking an original shoot on site), in-app inbox plus push notifications (Firebase Cloud Messaging) for friend requests, shares and achievements.

**Collection and progression**
Filterable grid (All / Original / Shared), sorting by name, rarity or date, XP total and six unlockable achievements.

**Sensors and gestures**
Shake trigger to share a card using the accelerometer, orientation compass using the rotation-vector sensor.

**Staff tools**
Card placement on the map with live radius preview, collection statistics per card, and user management (roles and account status).

## Tech stack

- Kotlin client with Jetpack Compose (MVVM, Hilt dependency injection, coroutines and Flow)
- Flask REST API hosted on PythonAnywhere with a SQLite database
- Cloudinary for image hosting and rarity stylization
- Firebase Cloud Messaging for push notifications
- JWT authentication

## Setup

### Prerequisites

- Android Studio
- A Cloudinary account (cloud name + unsigned upload preset)
- A Firebase project with Cloud Messaging enabled
- A PythonAnywhere account for the backend

### Android setup

1. Copy `secrets.properties.example` to `secrets.properties` and fill in your values:

```properties
API_BASE_URL=https://yourusername.pythonanywhere.com/api/
CLOUDINARY_CLOUD_NAME=yourcloudname
CLOUDINARY_UPLOAD_PRESET=cardhunt_unsigned
```

2. Place your google-services.json in the app/ directory (download it from Firebase Console → Project Settings → Your apps).
3. Open the project in Android Studio, sync Gradle, and run on a device or emulator.

### Backend setup

See [backend/README.md](backend/README.md).