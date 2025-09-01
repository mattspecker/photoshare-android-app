import { WebPlugin } from '@capacitor/core';

import type { AppPermissionsPlugin } from './app-permissions';

export class AppPermissionsWeb extends WebPlugin implements AppPermissionsPlugin {
  async isFirstLaunch(): Promise<{ isFirstLaunch: boolean }> {
    // Web implementation - always return false for web
    return { isFirstLaunch: false };
  }

  async requestNotificationPermission(): Promise<{ granted: boolean; error?: string }> {
    // Web implementation using Notification API
    if ('Notification' in window) {
      try {
        const permission = await Notification.requestPermission();
        return { granted: permission === 'granted' };
      } catch (error) {
        return { 
          granted: false, 
          error: error instanceof Error ? error.message : 'Unknown error' 
        };
      }
    } else {
      return { granted: true }; // Web doesn't require explicit permission for most cases
    }
  }

  async requestCameraPermission(): Promise<{ granted: boolean; error?: string }> {
    // Web implementation - camera permission is requested when getUserMedia is called
    return { granted: true };
  }

  async requestPhotoPermission(): Promise<{ granted: boolean; error?: string }> {
    // Web implementation - photo access doesn't require explicit permission
    return { granted: true };
  }

  async requestPhotosPermission(): Promise<{ granted: boolean; error?: string }> {
    return this.requestPhotoPermission();
  }

  async requestPhotoLibraryPermission(): Promise<{ granted: boolean; error?: string }> {
    return this.requestPhotoPermission();
  }

  async markOnboardingComplete(): Promise<{ success: boolean }> {
    // Web implementation using localStorage
    try {
      localStorage.setItem('photoshare_onboarding_complete', 'true');
      return { success: true };
    } catch (error) {
      return { success: false };
    }
  }

  async isOnboardingComplete(): Promise<{ complete: boolean }> {
    // Web implementation using localStorage
    try {
      const complete = localStorage.getItem('photoshare_onboarding_complete') === 'true';
      return { complete };
    } catch (error) {
      return { complete: false };
    }
  }

  async openAppSettings(): Promise<{ success: boolean }> {
    // Web implementation - can't open app settings, but return success
    console.log('Web: Cannot open app settings from web platform');
    return { success: true };
  }

  async ping(): Promise<{ success: boolean; message: string }> {
    return { 
      success: true, 
      message: 'AppPermissions plugin is working on web!' 
    };
  }

  async debugPrefsState(): Promise<{ 
    isFirstLaunch: boolean; 
    onboardingComplete: boolean; 
    allPrefs: string; 
  }> {
    // Web implementation using localStorage
    try {
      const isFirstLaunch = !localStorage.getItem('photoshare_first_launch_done');
      const onboardingComplete = localStorage.getItem('photoshare_onboarding_complete') === 'true';
      const allPrefs = JSON.stringify({
        photoshare_first_launch_done: localStorage.getItem('photoshare_first_launch_done'),
        photoshare_onboarding_complete: localStorage.getItem('photoshare_onboarding_complete')
      });

      return { isFirstLaunch, onboardingComplete, allPrefs };
    } catch (error) {
      return {
        isFirstLaunch: true,
        onboardingComplete: false,
        allPrefs: 'Error accessing localStorage'
      };
    }
  }
}