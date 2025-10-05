// Plugin Verification Test for PhotoShare Android App
// This script tests all plugins to ensure they're properly registered and available

console.log('🔍 === PLUGIN VERIFICATION TEST STARTING ===');

// Test function to check plugin availability
async function testPluginAvailability() {
  const results = {
    timestamp: new Date().toISOString(),
    capacitorInfo: {},
    npmPlugins: {},
    customPlugins: {},
    bridgePlugins: {},
    issues: []
  };

  try {
    // 1. Check Capacitor core availability
    console.log('🔍 Testing Capacitor core...');
    if (typeof window.Capacitor !== 'undefined') {
      results.capacitorInfo = {
        available: true,
        platform: window.Capacitor.getPlatform(),
        isNativePlatform: window.Capacitor.isNativePlatform(),
        version: window.Capacitor.version || 'unknown'
      };
      console.log('✅ Capacitor core available:', results.capacitorInfo);
    } else {
      results.issues.push('❌ Capacitor core not available');
      console.error('❌ Capacitor core not available');
      return results;
    }

    // 2. Check Capacitor.Plugins object
    console.log('🔍 Testing Capacitor.Plugins object...');
    if (window.Capacitor.Plugins) {
      const pluginNames = Object.keys(window.Capacitor.Plugins);
      console.log('🔍 Available plugins in Capacitor.Plugins:', pluginNames);
      
      // Test each plugin
      for (const pluginName of pluginNames) {
        const plugin = window.Capacitor.Plugins[pluginName];
        console.log(`🔍 Testing plugin: ${pluginName}`, typeof plugin);
      }
    } else {
      results.issues.push('❌ Capacitor.Plugins object not available');
    }

    // 3. Test NPM Plugins (auto-registered by Capacitor)
    console.log('🔍 Testing NPM plugins...');
    
    const npmPluginsToTest = {
      'BarcodeScanner': '@capacitor-mlkit/barcode-scanning',
      'Camera': '@capacitor/camera', 
      'Device': '@capacitor/device',
      'PushNotifications': '@capacitor/push-notifications',
      'StatusBar': '@capacitor/status-bar',
      'SafeArea': '@capacitor-community/safe-area',
      'FirebaseAuthentication': '@capacitor-firebase/authentication',
      'PhotoEditor': '@capawesome/capacitor-photo-editor'
    };

    for (const [pluginName, npmPackage] of Object.entries(npmPluginsToTest)) {
      try {
        const isAvailable = window.Capacitor.isPluginAvailable(pluginName);
        const plugin = window.Capacitor.Plugins[pluginName];
        
        results.npmPlugins[pluginName] = {
          package: npmPackage,
          available: isAvailable,
          pluginObject: !!plugin,
          methods: plugin ? Object.getOwnPropertyNames(plugin).filter(name => typeof plugin[name] === 'function') : []
        };
        
        console.log(`${isAvailable ? '✅' : '❌'} ${pluginName}: available=${isAvailable}, object=${!!plugin}`);
        
        if (!isAvailable) {
          results.issues.push(`❌ ${pluginName} (${npmPackage}) not available`);
        }
      } catch (error) {
        results.npmPlugins[pluginName] = { error: error.message };
        results.issues.push(`❌ Error testing ${pluginName}: ${error.message}`);
      }
    }

    // 4. Test Custom Plugins (manually registered)
    console.log('🔍 Testing custom plugins...');
    
    const customPluginsToTest = {
      'EventPhotoPicker': 'Custom event-aware photo picker',
      'AppPermissions': 'Custom app permissions handler', 
      'EnhancedCamera': 'Custom camera with editing integration',
      'AutoUpload': 'Custom multi-event auto upload',
      'CameraPreviewReplacement': 'Custom camera preview replacement'
    };

    for (const [pluginName, description] of Object.entries(customPluginsToTest)) {
      try {
        const isAvailable = window.Capacitor.isPluginAvailable(pluginName);
        const plugin = window.Capacitor.Plugins[pluginName];
        
        results.customPlugins[pluginName] = {
          description: description,
          available: isAvailable,
          pluginObject: !!plugin,
          methods: plugin ? Object.getOwnPropertyNames(plugin).filter(name => typeof plugin[name] === 'function') : []
        };
        
        console.log(`${isAvailable ? '✅' : '❌'} ${pluginName}: available=${isAvailable}, object=${!!plugin}`);
        
        if (!isAvailable) {
          results.issues.push(`❌ ${pluginName} (${description}) not available`);
        }
      } catch (error) {
        results.customPlugins[pluginName] = { error: error.message };
        results.issues.push(`❌ Error testing ${pluginName}: ${error.message}`);
      }
    }

    // 5. Test Bridge Functions (from capacitor-plugins.js)
    console.log('🔍 Testing bridge functions...');
    
    const bridgeFunctionsToTest = {
      'CapacitorPlugins': 'window.CapacitorPlugins',
      'CapacitorApp': 'window.CapacitorApp',
      'PhotoShareCameraWithEditing': 'window.PhotoShareCameraWithEditing',
      'AppPermissions': 'window.AppPermissions'
    };

    for (const [functionName, path] of Object.entries(bridgeFunctionsToTest)) {
      try {
        const obj = window[functionName];
        const available = !!obj;
        
        results.bridgePlugins[functionName] = {
          path: path,
          available: available,
          type: typeof obj,
          methods: obj && typeof obj === 'object' ? Object.keys(obj) : []
        };
        
        console.log(`${available ? '✅' : '❌'} ${functionName}: available=${available}, type=${typeof obj}`);
        
        if (!available) {
          results.issues.push(`❌ Bridge function ${functionName} not available`);
        }
      } catch (error) {
        results.bridgePlugins[functionName] = { error: error.message };
        results.issues.push(`❌ Error testing bridge ${functionName}: ${error.message}`);
      }
    }

    // 6. Summary
    console.log('🔍 === PLUGIN VERIFICATION SUMMARY ===');
    console.log(`✅ NPM Plugins Working: ${Object.values(results.npmPlugins).filter(p => p.available).length}/${Object.keys(results.npmPlugins).length}`);
    console.log(`✅ Custom Plugins Working: ${Object.values(results.customPlugins).filter(p => p.available).length}/${Object.keys(results.customPlugins).length}`);
    console.log(`✅ Bridge Functions Working: ${Object.values(results.bridgePlugins).filter(p => p.available).length}/${Object.keys(results.bridgePlugins).length}`);
    
    if (results.issues.length > 0) {
      console.log('⚠️ Issues found:');
      results.issues.forEach(issue => console.log(`  ${issue}`));
    } else {
      console.log('🎉 All plugins verified successfully!');
    }

  } catch (error) {
    console.error('❌ Plugin verification failed:', error);
    results.issues.push(`❌ Verification error: ${error.message}`);
  }

  return results;
}

// Test individual plugin functions
async function testPluginFunctions() {
  console.log('🔍 === TESTING PLUGIN FUNCTIONS ===');
  
  const functionTests = [];

  // Test EventPhotoPicker
  if (window.Capacitor?.isPluginAvailable('EventPhotoPicker')) {
    try {
      // Test a safe method first
      const result = await window.Capacitor.Plugins.EventPhotoPicker.testPlugin();
      functionTests.push({ plugin: 'EventPhotoPicker', method: 'testPlugin', success: true, result });
      console.log('✅ EventPhotoPicker.testPlugin() works:', result);
    } catch (error) {
      functionTests.push({ plugin: 'EventPhotoPicker', method: 'testPlugin', success: false, error: error.message });
      console.error('❌ EventPhotoPicker.testPlugin() failed:', error);
    }
  }

  // Test AppPermissions
  if (window.AppPermissions?.ping) {
    try {
      const result = await window.AppPermissions.ping();
      functionTests.push({ plugin: 'AppPermissions', method: 'ping', success: true, result });
      console.log('✅ AppPermissions.ping() works:', result);
    } catch (error) {
      functionTests.push({ plugin: 'AppPermissions', method: 'ping', success: false, error: error.message });
      console.error('❌ AppPermissions.ping() failed:', error);
    }
  }

  // Test Device plugin
  if (window.Capacitor?.isPluginAvailable('Device')) {
    try {
      const result = await window.Capacitor.Plugins.Device.getInfo();
      functionTests.push({ plugin: 'Device', method: 'getInfo', success: true, result: { platform: result.platform } });
      console.log('✅ Device.getInfo() works:', result.platform);
    } catch (error) {
      functionTests.push({ plugin: 'Device', method: 'getInfo', success: false, error: error.message });
      console.error('❌ Device.getInfo() failed:', error);
    }
  }

  return functionTests;
}

// Main test function
async function runFullPluginVerification() {
  console.log('🚀 === FULL PLUGIN VERIFICATION STARTING ===');
  
  const availabilityResults = await testPluginAvailability();
  const functionResults = await testPluginFunctions();
  
  const fullResults = {
    ...availabilityResults,
    functionTests: functionResults,
    overallStatus: availabilityResults.issues.length === 0 ? 'SUCCESS' : 'ISSUES_FOUND'
  };

  // Display results in a nice alert for easy reading
  let summary = `🔍 PLUGIN VERIFICATION RESULTS\\n\\n`;
  summary += `Platform: ${fullResults.capacitorInfo.platform}\\n`;
  summary += `NPM Plugins: ${Object.values(fullResults.npmPlugins).filter(p => p.available).length}/${Object.keys(fullResults.npmPlugins).length} working\\n`;
  summary += `Custom Plugins: ${Object.values(fullResults.customPlugins).filter(p => p.available).length}/${Object.keys(fullResults.customPlugins).length} working\\n`;
  summary += `Bridge Functions: ${Object.values(fullResults.bridgePlugins).filter(p => p.available).length}/${Object.keys(fullResults.bridgePlugins).length} working\\n`;
  summary += `Function Tests: ${functionResults.filter(t => t.success).length}/${functionResults.length} passed\\n\\n`;
  
  if (fullResults.issues.length > 0) {
    summary += `Issues (${fullResults.issues.length}):`;
    fullResults.issues.slice(0, 5).forEach(issue => {
      summary += `\\n• ${issue.replace(/[❌✅]/g, '')}`;
    });
    if (fullResults.issues.length > 5) {
      summary += `\\n... and ${fullResults.issues.length - 5} more`;
    }
  } else {
    summary += `🎉 All plugins verified successfully!`;
  }

  alert(summary);
  
  console.log('📊 Full results object:', fullResults);
  return fullResults;
}

// Make functions available globally for console testing
window.testPluginAvailability = testPluginAvailability;
window.testPluginFunctions = testPluginFunctions; 
window.runFullPluginVerification = runFullPluginVerification;

console.log('🔍 Plugin verification functions loaded. Use:');
console.log('  • runFullPluginVerification() - Run complete test with alert');
console.log('  • testPluginAvailability() - Test plugin availability only');
console.log('  • testPluginFunctions() - Test plugin functions only');

// Auto-run verification after a delay to let everything load
setTimeout(() => {
  console.log('🔍 Auto-running plugin verification...');
  runFullPluginVerification();
}, 2000);