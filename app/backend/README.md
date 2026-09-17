# CardHunt Backend

Flask REST API for the CardHunt mobile game.

## Local Setup

1. Install Python 3.8+
2. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

3. sqlite3 cardhunt.db < schema.sql

4. Download firebase-service-account.json from Firebase Console:
   - Go to Project Settings → Service Accounts
   - Click "Generate new private key"
   - Save as firebase-service-account.json in this folder

5. Run the server:
   ```bash
   python app.py
   ```

## Deployment

This backend is designed to run on PythonAnywhere:
Upload all files except cardhunt.db and firebase-service-account.json
Install requirements via Bash console
Initialize database via
   ```bash
   python init_db.py
   python create_staff.py
   ```
Configure WSGI file to point to app.py:
   ```bash
   import sys
   path = '/home/yourusername'
   if path not in sys.path:
      sys.path.append(path)

   from app import app as application
   ```

## Environment Variables

Set these before running:
SECRET_KEY: JWT signing key
DATABASE_URL: Path to SQLite database (default: cardhunt.db)


