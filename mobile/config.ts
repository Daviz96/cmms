import Constants from 'expo-constants';
import AsyncStorage from '@react-native-async-storage/async-storage';

export const googleMapsConfig = {
  apiKey: process.env.GOOGLE_KEY
};

// Default API URL from Expo config
const defaultApiUrl = Constants.expoConfig.extra.API_URL;
export const IS_LOCALHOST = false;

// Function to get the API URL (either custom or default)
export const getApiUrl = async (): Promise<string> => {
  try {
    // Try to get custom URL from AsyncStorage
    const customUrl = await AsyncStorage.getItem('customApiUrl');

    // Use custom URL if available, otherwise use default
    const rawApiUrl = customUrl || defaultApiUrl;
    // MOD-015 (M-BUG-1): guard against an unset URL — in a dev build the baked
    // API_URL is undefined and, before a custom server is configured, customUrl
    // is also null, so `rawApiUrl` would be undefined. Return '' instead of
    // calling .endsWith() on undefined (which threw a TypeError at startup).
    if (!rawApiUrl) return '';
    return rawApiUrl.endsWith('/') ? rawApiUrl : rawApiUrl + '/';
  } catch (error) {
    // Fallback to default URL if there's an error
    const rawApiUrl = defaultApiUrl;
    if (!rawApiUrl) return '';
    return rawApiUrl.endsWith('/') ? rawApiUrl : rawApiUrl + '/';
  }
};
