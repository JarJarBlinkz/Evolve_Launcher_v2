#!/bin/sh

PACKAGE="com.jarjarblinkz.EvolveLauncher"
PERMISSION="android.permission.PACKAGE_USAGE_STATS"

echo "🔧 Setting up permissions for $PACKAGE..."
echo ""

# First, check if the package exists
if ! pm list packages | grep -q "$PACKAGE"; then
    echo "❌ ERROR: Package $PACKAGE is not installed!"
    echo "   Please install the launcher first and try again."
    exit 1
fi

# Method 1: Try pm grant
echo "📱 Method 1: Using pm grant..."
pm grant "$PACKAGE" "$PERMISSION" 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ Method 1 succeeded!"
    GRANT_SUCCESS=1
else
    echo "⏭️  Method 1 failed (expected on some Android versions)"
    GRANT_SUCCESS=0
fi

# Method 2: Try appops (often works when pm grant fails)
echo ""
echo "📱 Method 2: Using appops..."
appops set "$PACKAGE" GET_USAGE_STATS allow 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ Method 2 succeeded!"
    APPOPS_SUCCESS=1
else
    echo "❌ Method 2 failed"
    APPOPS_SUCCESS=0
fi

# Final verification
echo ""
echo "🔍 Verifying permissions..."
echo "------------------------"

# Check via appops
USAGE_STATS_STATUS=$(appops get "$PACKAGE" | grep -i "usage_stats" | awk '{print $5}')
if [ "$USAGE_STATS_STATUS" = "allow" ]; then
    echo "✅ GET_USAGE_STATS: GRANTED (via appops)"
    FINAL_SUCCESS=1
else
    echo "❌ GET_USAGE_STATS: NOT GRANTED"
    FINAL_SUCCESS=0
fi

# Also check package manager view
if [ $GRANT_SUCCESS -eq 1 ]; then
    echo "✅ Package Manager: Permission shows as granted"
fi

echo "------------------------"
echo ""

# Final message
if [ $FINAL_SUCCESS -eq 1 ]; then
    echo "✨ SUCCESS! Permission has been granted."
    echo "   Your launcher should now have access to usage statistics."
    echo ""
    echo "📝 Note: This permission persists through reboots."
else
    echo "⚠️  WARNING: Could not verify permission was granted."
    echo ""
    echo "Possible issues:"
    echo "  • The app might not be installed correctly"
    echo "  • This Quest model might restrict this permission"
    echo "  • You may need to restart the headset"
    echo ""
    echo "Try restarting your Quest and running this installer again."
fi

echo ""
echo "✓ INSTALLATION COMPLETE"
sleep 3