import * as Updates from 'expo-updates';
import { useEffect } from 'react';
import { AppState, Pressable, StyleSheet, Text, View } from 'react-native';

// Clinic devices (reception PC, chairside tablets) can stay open all day, so
// besides the check on launch we also check whenever the app returns to the
// foreground, then offer a one-tap restart once the new bundle is downloaded.
export function UpdateBanner() {
  const { isUpdatePending } = Updates.useUpdates();

  useEffect(() => {
    if (!Updates.isEnabled) return;

    const checkAndFetch = async () => {
      try {
        const result = await Updates.checkForUpdateAsync();
        if (result.isAvailable) await Updates.fetchUpdateAsync();
      } catch {
        // Offline or update server unreachable: try again next time.
      }
    };

    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') checkAndFetch();
    });
    return () => sub.remove();
  }, []);

  if (!isUpdatePending) return null;

  return (
    <View style={styles.banner}>
      <Text style={styles.text}>A new version of Meno is ready.</Text>
      <Pressable
        accessibilityRole="button"
        onPress={() => Updates.reloadAsync()}
        style={styles.button}
      >
        <Text style={styles.buttonText}>Restart</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: '#111827',
  },
  text: {
    color: '#FFFFFF',
    fontSize: 14,
    flexShrink: 1,
  },
  button: {
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: 16,
    borderRadius: 8,
    backgroundColor: '#FFFFFF',
  },
  buttonText: {
    color: '#111827',
    fontSize: 14,
    fontWeight: '600',
  },
});
