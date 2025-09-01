import { registerPlugin } from '@capacitor/core';

export interface AppPermissionsPlugin {
  /**
   * Check if this is the first launch of the app
   */
  isFirstLaunch(): Promise<{ isFirstLaunch: boolean }>;

  /**
   * Request notification permission
   */
  requestNotificationPermission(): Promise<{ granted: boolean; error?: string }>;

  /**
   * Request camera permission
   */
  requestCameraPermission(): Promise<{ granted: boolean; error?: string }>;

  /**
   * Request photo/gallery permission
   */
  requestPhotoPermission(): Promise<{ granted: boolean; error?: string }>;

  /**
   * Request photo/gallery permission (alternative name)
   */
  requestPhotosPermission(): Promise<{ granted: boolean; error?: string }>;

  /**
   * Request photo/gallery permission (iOS compatible name)
   */
  requestPhotoLibraryPermission(): Promise<{ granted: boolean; error?: string }>;

  /**
   * Mark onboarding as complete
   */
  markOnboardingComplete(): Promise<{ success: boolean }>;

  /**
   * Check if onboarding is complete
   */
  isOnboardingComplete(): Promise<{ complete: boolean }>;

  /**
   * Open app settings (for when permissions are permanently denied)
   */
  openAppSettings(): Promise<{ success: boolean }>;

  /**
   * Simple ping method to test if plugin is working
   */
  ping(): Promise<{ success: boolean; message: string }>;

  /**
   * Debug method to check SharedPreferences state
   */
  debugPrefsState(): Promise<{ 
    isFirstLaunch: boolean; 
    onboardingComplete: boolean; 
    allPrefs: string; 
  }>;
}

const AppPermissions = registerPlugin<AppPermissionsPlugin>('AppPermissions', {
  web: () => import('./web').then(m => new m.AppPermissionsWeb()),
});

export default AppPermissions;