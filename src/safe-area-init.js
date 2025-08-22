// Safe Area Plugin Initialization for WebView
import { SafeArea } from '@capacitor-community/safe-area';
import { Capacitor } from '@capacitor/core';

// Initialize safe area for external web content
function initializeSafeArea() {
  if (Capacitor.isNativePlatform()) {
    // Enable the safe area plugin
    SafeArea.enable({
      config: {
        customColorsForSystemBars: true,
        statusBarColor: '#00000000', // transparent
        statusBarContent: 'light',
        navigationBarColor: '#00000000', // transparent 
        navigationBarContent: 'light',
      },
    });

    // Inject CSS styles for safe area insets
    const style = document.createElement('style');
    style.textContent = `
      /* Apply safe area insets to the body or main container */
      body {
        padding-top: var(--safe-area-inset-top) !important;
        padding-right: var(--safe-area-inset-right) !important;
        padding-bottom: var(--safe-area-inset-bottom) !important;
        padding-left: var(--safe-area-inset-left) !important;
      }
      
      /* Specific styles for PhotoShare header areas */
      .header, .navbar, .app-header, [data-testid="header"] {
        padding-top: calc(var(--safe-area-inset-top, 0px) + 8px) !important;
      }
      
      /* Ensure content doesn't overlap with system UI */
      .content, .main-content, .app-content {
        margin-top: var(--safe-area-inset-top, 0px) !important;
      }
    `;
    document.head.appendChild(style);

    console.log('Safe Area plugin initialized for WebView');
  }
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initializeSafeArea);
} else {
  initializeSafeArea();
}

export { initializeSafeArea };