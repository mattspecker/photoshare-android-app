/**
 * Enhanced Camera Editor Component
 * 
 * Combines Capacitor Community Camera Preview with Fabric.js editing
 * for PhotoShare app with stickers, text, fonts, and color pickers.
 * 
 * Features:
 * - Live camera preview with custom UI
 * - Photo capture and editing with Fabric.js
 * - Sticker picker with drag-and-drop
 * - Editable text with font and color selection
 * - Integration with PhotoShare upload system
 */

import { CameraPreview } from '@capacitor-community/camera-preview';
import { fabric } from 'fabric';

// PhotoShare sticker collection
const PHOTOSHARE_STICKERS = [
  '/stickers/star.png',
  '/stickers/heart.png', 
  '/stickers/smile.png',
  '/stickers/camera.png',
  '/stickers/party.png',
  '/stickers/celebration.png'
];

// PhotoShare font collection
const PHOTOSHARE_FONTS = [
  'Arial',
  'Helvetica', 
  'Comic Sans MS',
  'Courier New',
  'Georgia',
  'Times New Roman',
  'Impact',
  'Trebuchet MS'
];

// PhotoShare color palette
const PHOTOSHARE_COLORS = [
  '#000000', '#FFFFFF', '#FF0000', '#00FF00', 
  '#0000FF', '#FFFF00', '#FF00FF', '#00FFFF',
  '#FFA500', '#800080', '#FFC0CB', '#A52A2A'
];

class PhotoShareEnhancedCamera {
  constructor(options = {}) {
    this.options = {
      quality: 90,
      enableEditing: true,
      showStickers: true,
      showTextEditor: true,
      ...options
    };
    
    this.isEditing = false;
    this.canvas = null;
    this.selectedColor = PHOTOSHARE_COLORS[0];
    this.selectedFont = PHOTOSHARE_FONTS[0];
    this.capturedImageData = null;
    
    // Callbacks
    this.onPhotoSaved = options.onPhotoSaved || this.defaultPhotoSaved;
    this.onCameraError = options.onCameraError || this.defaultCameraError;
    
    this.init();
  }
  
  async init() {
    console.log('📸 ===== PHOTOSHARE ENHANCED CAMERA INITIALIZING =====');
    
    try {
      // Check if enhanced camera plugin is available
      if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.EnhancedCamera) {
        const availability = await window.Capacitor.Plugins.EnhancedCamera.isEnhancedCameraAvailable();
        console.log('📸 Enhanced Camera availability:', availability);
      }
      
      this.createCameraUI();
      await this.startCameraPreview();
      
    } catch (error) {
      console.error('📸 ❌ Enhanced Camera initialization failed:', error);
      this.onCameraError(error);
    }
  }
  
  createCameraUI() {
    console.log('📸 Creating PhotoShare enhanced camera UI');
    
    // Create main container
    this.container = document.createElement('div');
    this.container.id = 'photoShareEnhancedCamera';
    this.container.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      width: 100vw;
      height: 100vh;
      z-index: 10000;
      background: #000;
      display: flex;
      flex-direction: column;
    `;
    
    // Create camera preview container
    this.previewContainer = document.createElement('div');
    this.previewContainer.id = 'photoShareCameraPreview';
    this.previewContainer.style.cssText = `
      position: relative;
      flex: 1;
      width: 100%;
      background: #000;
    `;
    
    // Create canvas for editing (hidden initially)
    this.canvasContainer = document.createElement('div');
    this.canvasContainer.id = 'photoShareCanvasContainer';
    this.canvasContainer.style.cssText = `
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: #000;
      display: none;
      z-index: 2;
    `;
    
    this.canvasElement = document.createElement('canvas');
    this.canvasElement.id = 'photoShareEditCanvas';
    this.canvasContainer.appendChild(this.canvasElement);
    
    // Create camera controls
    this.createCameraControls();
    
    // Create editing controls (hidden initially)
    this.createEditingControls();
    
    // Assemble UI
    this.container.appendChild(this.previewContainer);
    this.container.appendChild(this.canvasContainer);
    document.body.appendChild(this.container);
  }
  
  createCameraControls() {
    this.cameraControls = document.createElement('div');
    this.cameraControls.id = 'photoShareCameraControls';
    this.cameraControls.style.cssText = `
      position: absolute;
      bottom: 20px;
      left: 50%;
      transform: translateX(-50%);
      display: flex;
      gap: 20px;
      z-index: 3;
    `;
    
    // Capture button
    this.captureButton = document.createElement('button');
    this.captureButton.innerHTML = '📸 Capture';
    this.captureButton.style.cssText = `
      background: #007AFF;
      color: white;
      border: none;
      border-radius: 25px;
      padding: 12px 24px;
      font-size: 18px;
      font-weight: bold;
      cursor: pointer;
      box-shadow: 0 4px 12px rgba(0,122,255,0.3);
    `;
    this.captureButton.onclick = () => this.capturePhoto();
    
    // Close button
    this.closeButton = document.createElement('button');
    this.closeButton.innerHTML = '❌ Close';
    this.closeButton.style.cssText = `
      background: #FF3B30;
      color: white;
      border: none;
      border-radius: 25px;
      padding: 12px 24px;
      font-size: 18px;
      font-weight: bold;
      cursor: pointer;
      box-shadow: 0 4px 12px rgba(255,59,48,0.3);
    `;
    this.closeButton.onclick = () => this.closeCamera();
    
    this.cameraControls.appendChild(this.captureButton);
    this.cameraControls.appendChild(this.closeButton);
    this.previewContainer.appendChild(this.cameraControls);
  }
  
  createEditingControls() {
    this.editingControls = document.createElement('div');
    this.editingControls.id = 'photoShareEditingControls';
    this.editingControls.style.cssText = `
      position: absolute;
      bottom: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: rgba(0,0,0,0.8);
      border-radius: 15px;
      padding: 15px;
      display: none;
      flex-direction: column;
      gap: 15px;
      z-index: 4;
      max-width: 90vw;
    `;
    
    // Sticker picker
    if (this.options.showStickers) {
      this.createStickerPicker();
    }
    
    // Text controls
    if (this.options.showTextEditor) {
      this.createTextControls();
    }
    
    // Action buttons
    this.createActionButtons();
    
    this.canvasContainer.appendChild(this.editingControls);
  }
  
  createStickerPicker() {
    const stickerSection = document.createElement('div');
    stickerSection.innerHTML = '<div style=\"color: white; font-weight: bold; margin-bottom: 10px;\">📝 Stickers</div>';
    
    const stickerContainer = document.createElement('div');
    stickerContainer.style.cssText = `
      display: flex;
      gap: 10px;
      overflow-x: auto;
      padding: 5px;
    `;
    
    PHOTOSHARE_STICKERS.forEach(stickerSrc => {
      const stickerImg = document.createElement('img');
      stickerImg.src = stickerSrc;
      stickerImg.style.cssText = `
        width: 50px;
        height: 50px;
        cursor: pointer;
        border-radius: 8px;
        transition: transform 0.2s;
        object-fit: contain;
        background: rgba(255,255,255,0.1);
        padding: 5px;
      `;
      stickerImg.onmouseover = () => stickerImg.style.transform = 'scale(1.1)';
      stickerImg.onmouseout = () => stickerImg.style.transform = 'scale(1)';
      stickerImg.onclick = () => this.addSticker(stickerSrc);
      
      stickerContainer.appendChild(stickerImg);
    });
    
    stickerSection.appendChild(stickerContainer);
    this.editingControls.appendChild(stickerSection);
  }
  
  createTextControls() {
    const textSection = document.createElement('div');
    textSection.innerHTML = '<div style=\"color: white; font-weight: bold; margin-bottom: 10px;\">✏️ Text Editor</div>';
    
    const textControls = document.createElement('div');
    textControls.style.cssText = `
      display: flex;
      gap: 10px;
      align-items: center;
      flex-wrap: wrap;
    `;
    
    // Add text button
    const addTextBtn = document.createElement('button');
    addTextBtn.innerHTML = '+ Text';
    addTextBtn.style.cssText = `
      background: #34C759;
      color: white;
      border: none;
      border-radius: 8px;
      padding: 8px 12px;
      cursor: pointer;
      font-weight: bold;
    `;
    addTextBtn.onclick = () => this.addText();
    
    // Color picker
    const colorPicker = document.createElement('select');
    colorPicker.style.cssText = `
      padding: 8px;
      border-radius: 8px;
      border: none;
      background: white;
    `;
    PHOTOSHARE_COLORS.forEach(color => {
      const option = document.createElement('option');
      option.value = color;
      option.textContent = color;
      option.style.backgroundColor = color;
      option.style.color = color === '#000000' ? 'white' : 'black';
      colorPicker.appendChild(option);
    });
    colorPicker.onchange = (e) => this.updateColor(e.target.value);
    
    // Font picker
    const fontPicker = document.createElement('select');
    fontPicker.style.cssText = `
      padding: 8px;
      border-radius: 8px;
      border: none;
      background: white;
    `;
    PHOTOSHARE_FONTS.forEach(font => {
      const option = document.createElement('option');
      option.value = font;
      option.textContent = font;
      option.style.fontFamily = font;
      fontPicker.appendChild(option);
    });
    fontPicker.onchange = (e) => this.updateFont(e.target.value);
    
    textControls.appendChild(addTextBtn);
    textControls.appendChild(colorPicker);
    textControls.appendChild(fontPicker);
    
    textSection.appendChild(textControls);
    this.editingControls.appendChild(textSection);
  }
  
  createActionButtons() {
    const actionSection = document.createElement('div');
    actionSection.style.cssText = `
      display: flex;
      gap: 15px;
      justify-content: center;
      margin-top: 10px;
    `;
    
    // Save button
    const saveButton = document.createElement('button');
    saveButton.innerHTML = '💾 Save Photo';
    saveButton.style.cssText = `
      background: #007AFF;
      color: white;
      border: none;
      border-radius: 12px;
      padding: 12px 20px;
      font-weight: bold;
      cursor: pointer;
    `;
    saveButton.onclick = () => this.saveEditedPhoto();
    
    // Cancel button
    const cancelButton = document.createElement('button');
    cancelButton.innerHTML = '🔙 Retake';
    cancelButton.style.cssText = `
      background: #FF9500;
      color: white;
      border: none;
      border-radius: 12px;
      padding: 12px 20px;
      font-weight: bold;
      cursor: pointer;
    `;
    cancelButton.onclick = () => this.retakePhoto();
    
    actionSection.appendChild(saveButton);
    actionSection.appendChild(cancelButton);
    this.editingControls.appendChild(actionSection);
  }
  
  async startCameraPreview() {
    console.log('🔥 📸 CAMERA PREVIEW: Starting PhotoShare camera preview');
    console.log('🔥 📸 ENGINE: @capacitor-community/camera-preview');
    console.log('🔥 📸 CALLING: CameraPreview.start()');
    
    try {
      await CameraPreview.start({
        parent: 'photoShareCameraPreview',
        position: 'rear',
        toBack: true,
        width: window.innerWidth,
        height: window.innerHeight - 100, // Leave space for controls
      });
      
      console.log('📸 ✅ Camera preview started successfully');
      
      // Notify enhanced camera plugin
      if (window.Capacitor?.Plugins?.EnhancedCamera) {
        await window.Capacitor.Plugins.EnhancedCamera.startEnhancedPreview({
          position: 'rear',
          width: window.innerWidth,
          height: window.innerHeight,
          toBack: true
        });
      }
      
    } catch (error) {
      console.error('📸 ❌ Failed to start camera preview:', error);
      this.onCameraError(error);
    }
  }
  
  async capturePhoto() {
    console.log('🔥 📸 CAPTURE: Capturing PhotoShare enhanced photo');
    console.log('🔥 📸 ENGINE: @capacitor-community/camera-preview');
    console.log('🔥 📸 CALLING: CameraPreview.capture()');
    
    try {
      // Capture photo using CameraPreview
      const result = await CameraPreview.capture({ 
        quality: this.options.quality 
      });
      
      console.log('📸 ✅ Photo captured successfully');
      
      // Stop camera preview
      await CameraPreview.stop();
      
      // Store captured image data
      this.capturedImageData = 'data:image/jpeg;base64,' + result.value;
      
      // Switch to editing mode
      this.switchToEditingMode();
      
      // Notify enhanced camera plugin
      if (window.Capacitor?.Plugins?.EnhancedCamera) {
        await window.Capacitor.Plugins.EnhancedCamera.captureEnhancedPhoto({
          quality: this.options.quality,
          format: 'jpeg',
          enableEditing: this.options.enableEditing
        });
      }
      
    } catch (error) {
      console.error('📸 ❌ Failed to capture photo:', error);
      this.onCameraError(error);
    }
  }
  
  switchToEditingMode() {
    console.log('📸 Switching to PhotoShare editing mode');
    
    this.isEditing = true;
    
    // Hide camera preview and controls
    this.previewContainer.style.display = 'none';
    
    // Show canvas and editing controls
    this.canvasContainer.style.display = 'block';
    this.editingControls.style.display = 'flex';
    
    // Initialize Fabric.js canvas
    this.initializeFabricCanvas();
  }
  
  initializeFabricCanvas() {
    console.log('📸 Initializing PhotoShare Fabric.js canvas');
    
    const img = new Image();
    img.onload = () => {
      // Create Fabric canvas
      this.canvas = new fabric.Canvas(this.canvasElement, {
        width: img.width,
        height: img.height,
      });
      
      // Set captured image as background
      this.canvas.setBackgroundImage(img.src, this.canvas.renderAll.bind(this.canvas), {
        scaleX: this.canvas.width / img.width,
        scaleY: this.canvas.height / img.height,
      });
      
      console.log('📸 ✅ Fabric.js canvas initialized with captured image');
    };
    
    img.src = this.capturedImageData;
  }
  
  addSticker(stickerSrc) {
    console.log('📸 Adding sticker to PhotoShare canvas:', stickerSrc);
    
    if (!this.canvas) return;
    
    fabric.Image.fromURL(stickerSrc, (img) => {
      img.left = Math.random() * (this.canvas.width - 100);
      img.top = Math.random() * (this.canvas.height - 100);
      img.scale(0.5);
      img.set({
        cornerColor: '#007AFF',
        cornerSize: 10,
        transparentCorners: false
      });
      
      this.canvas.add(img);
      this.canvas.setActiveObject(img);
      this.canvas.renderAll();
      
      console.log('📸 ✅ Sticker added successfully');
    });
  }
  
  addText() {
    console.log('📸 Adding text to PhotoShare canvas');
    
    if (!this.canvas) return;
    
    const text = new fabric.IText('PhotoShare Text', {
      left: 100,
      top: 100,
      fill: this.selectedColor,
      fontFamily: this.selectedFont,
      fontSize: 30,
      fontWeight: 'bold',
      cornerColor: '#007AFF',
      cornerSize: 10,
      transparentCorners: false
    });
    
    this.canvas.add(text);
    this.canvas.setActiveObject(text);
    this.canvas.renderAll();
    
    console.log('📸 ✅ Text added successfully');
  }
  
  updateColor(color) {
    this.selectedColor = color;
    console.log('📸 Updating text color:', color);
    
    const active = this.canvas?.getActiveObject();
    if (active && active.isType('text')) {
      active.set('fill', color);
      this.canvas.renderAll();
      console.log('📸 ✅ Text color updated');
    }
  }
  
  updateFont(font) {
    this.selectedFont = font;
    console.log('📸 Updating text font:', font);
    
    const active = this.canvas?.getActiveObject();
    if (active && active.isType('text')) {
      active.set('fontFamily', font);
      this.canvas.renderAll();
      console.log('📸 ✅ Text font updated');
    }
  }
  
  saveEditedPhoto() {
    console.log('📸 Saving PhotoShare edited photo');
    
    if (!this.canvas) return;
    
    try {
      const dataUrl = this.canvas.toDataURL({
        format: 'png',
        quality: 0.9,
        multiplier: 1
      });
      
      console.log('📸 ✅ Photo edited and saved successfully');
      
      // Call callback with saved photo data
      this.onPhotoSaved({
        success: true,
        dataUrl: dataUrl,
        format: 'png',
        quality: 0.9,
        editingFeatures: ['stickers', 'text'],
        timestamp: new Date().toISOString()
      });
      
      // Close camera
      this.closeCamera();
      
    } catch (error) {
      console.error('📸 ❌ Failed to save edited photo:', error);
      this.onCameraError(error);
    }
  }
  
  async retakePhoto() {
    console.log('📸 Retaking PhotoShare photo');
    
    // Reset editing state
    this.isEditing = false;
    this.capturedImageData = null;
    
    if (this.canvas) {
      this.canvas.dispose();
      this.canvas = null;
    }
    
    // Hide editing UI
    this.canvasContainer.style.display = 'none';
    this.editingControls.style.display = 'none';
    
    // Show camera preview
    this.previewContainer.style.display = 'block';
    
    // Restart camera preview
    await this.startCameraPreview();
  }
  
  async closeCamera() {
    console.log('📸 Closing PhotoShare enhanced camera');
    
    try {
      // Stop camera preview
      await CameraPreview.stop();
      
      // Notify enhanced camera plugin
      if (window.Capacitor?.Plugins?.EnhancedCamera) {
        await window.Capacitor.Plugins.EnhancedCamera.stopEnhancedPreview();
      }
      
      // Clean up canvas
      if (this.canvas) {
        this.canvas.dispose();
        this.canvas = null;
      }
      
      // Remove UI
      if (this.container && this.container.parentNode) {
        this.container.parentNode.removeChild(this.container);
      }
      
      console.log('📸 ✅ Enhanced camera closed successfully');
      
    } catch (error) {
      console.error('📸 ❌ Error closing camera:', error);
    }
  }
  
  // Default callbacks
  defaultPhotoSaved(result) {
    console.log('📸 PhotoShare Enhanced Camera - Photo saved:', result);
    alert('Photo saved successfully! 📸✨');
  }
  
  defaultCameraError(error) {
    console.error('📸 PhotoShare Enhanced Camera Error:', error);
    alert('Camera error: ' + error.message);
  }
}

// Export for PhotoShare integration
if (typeof window !== 'undefined') {
  window.PhotoShareEnhancedCamera = PhotoShareEnhancedCamera;
}

// Export for ES6 modules  
export default PhotoShareEnhancedCamera;

console.log('📸 PhotoShare Enhanced Camera Editor loaded successfully');