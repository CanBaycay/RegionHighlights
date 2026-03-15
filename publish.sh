#!/bin/bash
set -euo pipefail

CONFIG_FILE="publish.config"
SAMPLE_CONFIG_FILE="publish.config.sample"
BUILD_GRADLE="build.gradle.kts"

# --- Config loading ---

if [ ! -f "$CONFIG_FILE" ]; then
    echo "Missing '$CONFIG_FILE'."
    echo ""

    if [ ! -f "$SAMPLE_CONFIG_FILE" ]; then
        echo "Creating '$SAMPLE_CONFIG_FILE' for reference..."
        cat > "$SAMPLE_CONFIG_FILE" <<'EOF'
# Get your token from: https://plugins.jetbrains.com/author/me/tokens
PUBLISH_TOKEN=your_publish_token_here

# Generate keys: https://plugins.jetbrains.com/docs/intellij/plugin-signing.html#generate-private-key
CERTIFICATE_CHAIN=/path/to/chain.crt
PRIVATE_KEY=/path/to/private.pem
PRIVATE_KEY_PASSWORD=your_password_here
EOF
        echo "Created '$SAMPLE_CONFIG_FILE'."
    else
        echo "See '$SAMPLE_CONFIG_FILE' for the required format."
    fi

    echo ""
    echo "Copy it and fill in your values:"
    echo "  cp $SAMPLE_CONFIG_FILE $CONFIG_FILE"
    exit 1
fi

source "$CONFIG_FILE"

for var in PUBLISH_TOKEN CERTIFICATE_CHAIN PRIVATE_KEY PRIVATE_KEY_PASSWORD; do
    if [ -z "${!var:-}" ]; then
        echo "Error: '$var' is not set in $CONFIG_FILE"
        exit 1
    fi
done

export PUBLISH_TOKEN CERTIFICATE_CHAIN PRIVATE_KEY PRIVATE_KEY_PASSWORD

# --- Version bump ---

current_version=$(grep -E '^version\s*=' "$BUILD_GRADLE" | sed 's/.*"\(.*\)".*/\1/')
echo "Current version: $current_version"
echo ""

read -rp "Increment patch version? (y/N): " bump
if [[ "$bump" =~ ^[Yy]$ ]]; then
    IFS='.' read -r major minor patch <<< "$current_version"
    new_version="$major.$minor.$((patch + 1))"

    sed -i '' "s/version = \"$current_version\"/version = \"$new_version\"/" "$BUILD_GRADLE"

    echo "Version updated: $current_version -> $new_version"
    git add "$BUILD_GRADLE"
    git commit -m "Bump version to $new_version"
    echo "Committed version bump."
    echo ""
    current_version="$new_version"
fi

# --- Build ---

echo "Building plugin v$current_version..."
echo ""
./gradlew clean buildPlugin signPlugin

# --- Next steps ---

echo ""
echo "================================================"
echo "  Build complete! v$current_version"
echo "================================================"
echo ""
echo "The signed plugin zip is at:"
echo "  build/distributions/RegionHighlights-$current_version-signed.zip"
echo ""
echo "To publish automatically:"
echo "  ./gradlew publishPlugin"
echo ""
echo "To publish manually:"
echo "  1. Go to https://plugins.jetbrains.com/plugin/add"
echo "  2. Upload the signed zip file above"
echo "  3. Fill in the plugin details and submit for review"
echo ""
