#!/bin/bash
# Generate self-signed TLS certificates for Chorus Observe Server
# Usage: ./scripts/generate-certs.sh [output-dir]
# Default output: ./certs/

set -euo pipefail

OUT_DIR="${1:-./certs}"
KEYSTORE_PASS="${KEYSTORE_PASSWORD:-changeit}"
ALIAS="chorus-observe"
KEYSTORE="$OUT_DIR/keystore.p12"

mkdir -p "$OUT_DIR"

echo "Generating self-signed certificate for Chorus Observe Server..."
echo "  Output: $KEYSTORE"
echo "  Alias:  $ALIAS"
echo "  Pass:   $KEYSTORE_PASS"

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 365 \
  -storetype PKCS12 \
  -keystore "$KEYSTORE" \
  -storepass "$KEYSTORE_PASS" \
  -keypass "$KEYSTORE_PASS" \
  -dname "CN=chorus-observe, OU=Platform, O=Chorus, L=San Francisco, ST=CA, C=US" \
  -ext "san=dns:localhost,ip:127.0.0.1"

echo ""
echo "Certificate generated successfully."
echo ""
echo "To enable TLS, add to your application.yml or docker-compose env:"
echo "  SERVER_SSL_ENABLED=true"
echo "  SERVER_SSL_KEY_STORE=file:/app/certs/keystore.p12"
echo "  SERVER_SSL_KEY_STORE_PASSWORD=$KEYSTORE_PASS"
echo "  SERVER_SSL_KEY_STORE_TYPE=PKCS12"
echo "  SERVER_SSL_KEY_ALIAS=$ALIAS"
