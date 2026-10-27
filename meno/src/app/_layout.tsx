import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';

import { UpdateBanner } from '../components/UpdateBanner';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <SafeAreaView style={{ flex: 1 }} edges={['top']}>
        <UpdateBanner />
        <Stack screenOptions={{ headerShown: false }} />
      </SafeAreaView>
      <StatusBar style="auto" />
    </SafeAreaProvider>
  );
}
