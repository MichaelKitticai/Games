import Constants from 'expo-constants';
import * as Updates from 'expo-updates';
import { StyleSheet, Text, View } from 'react-native';

// Placeholder until a homepage design (A, B or C) is chosen. The build info
// makes it easy to confirm on a device that an OTA update has landed.
export default function Home() {
  const version = Constants.expoConfig?.version ?? 'dev';
  const channel = Updates.channel || 'local';
  const updateId = Updates.updateId?.slice(0, 8) || 'embedded';

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Meno</Text>
      <Text style={styles.subtitle}>Dental clinic scheduling for Nairobi</Text>
      <Text style={styles.meta}>
        v{version} · {channel} · {updateId}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    padding: 24,
    backgroundColor: '#F7F8FA',
  },
  title: {
    fontSize: 32,
    fontWeight: '700',
    color: '#111827',
  },
  subtitle: {
    fontSize: 16,
    color: '#4B5563',
  },
  meta: {
    marginTop: 16,
    fontSize: 12,
    color: '#4B5563',
  },
});
