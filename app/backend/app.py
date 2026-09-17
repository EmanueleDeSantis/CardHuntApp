from flask import Flask, jsonify
from flask_cors import CORS

from auth import auth_bp
from cards import cards_bp
from social import social_bp
from staff import staff_bp


def create_app():
    app = Flask(__name__)
    CORS(app)
    app.register_blueprint(auth_bp)
    app.register_blueprint(cards_bp)
    app.register_blueprint(social_bp)
    app.register_blueprint(staff_bp)

    @app.get("/api/health")
    def health():
        return jsonify(status="ok")

    @app.errorhandler(404)
    def not_found(_e):
        return jsonify(error="Not found"), 404

    @app.errorhandler(500)
    def server_error(_e):
        return jsonify(error="Server error"), 500

    return app


app = create_app()