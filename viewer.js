// Minecraft Map Viewer Engine
(function() {
  'use strict';

  // Load metadata from global or fetch
  const meta = window.MAP_METADATA;
  if (!meta) {
    console.error('Map metadata not found!');
    return;
  }

  const canvas = document.getElementById('map-canvas');
  const ctx = canvas.getContext('2d');
  const minimapCanvas = document.getElementById('minimap-canvas');
  const minimapCtx = minimapCanvas.getContext('2d');

  // State
  let camera = {
    x: meta.width / 2,
    y: meta.height / 2,
    zoom: 0.15,
    targetX: meta.width / 2,
    targetY: meta.height / 2,
    targetZoom: 0.15,
  };

  let isDragging = false;
  let dragStart = { x: 0, y: 0 };
  let cameraDragStart = { x: 0, y: 0 };
  let hoveredImage = null;
  let hoveredPoi = null;
  let showScreenshotGrid = false;
  let showPois = true;
  let pixelArtMode = true;
  let measureMode = false;
  let measurePoints = [];
  let userWaypoints = [];

  // Load waypoints from localStorage
  try {
    const saved = localStorage.getItem('mc_map_waypoints');
    if (saved) userWaypoints = JSON.parse(saved);
  } catch(e) {}

  // Image assets
  let activeImage = null;
  let fullImageLoaded = false;

  const previewImg = new Image();
  const fullImg = new Image();

  // Full-Resolution Slippy Tile Streamer (1:1 Original Master Detail)
  const TILE_SIZE = 512;
  const numRows = Math.ceil(meta.height / TILE_SIZE);
  const numCols = Math.ceil(meta.width / TILE_SIZE);
  const tileCache = new Map();

  function getTile(row, col) {
    if (row < 0 || row >= numRows || col < 0 || col >= numCols) return null;
    const key = `${row}_${col}`;
    if (tileCache.has(key)) {
      return tileCache.get(key);
    }
    const img = new Image();
    img.src = `tiles/tile_${row}_${col}.jpg`;
    img.onload = () => {
      tileCache.set(key, img);
      requestRender();
    };
    img.onerror = () => {
      tileCache.set(key, false);
    };
    tileCache.set(key, null); // marked as in-flight
    return null;
  }

  // Color palette for screenshot columns (13 distinct tones)
  const columnColors = [
    '#4299e1', // Blue (Col 1)
    '#48bb78', // Green (Col 2)
    '#ecc94b', // Yellow (Col 3)
    '#ed8936', // Orange (Col 4)
    '#9f7aea', // Purple (Col 5)
    '#f56565', // Red (Col 6)
    '#38b2ac', // Teal (Col 7)
    '#ed64a6', // Pink (Col 8)
    '#667eea', // Indigo (Col 9)
    '#f6ad55', // Amber (Col 10)
    '#68d391', // Mint (Col 11)
    '#fc8181', // Coral (Col 12)
    '#b794f4'  // Violet (Col 13)
  ];

  function getScreenshotColor(item) {
    if (item && typeof item === 'object' && item.filename) {
      const match = item.filename.match(/col(\d+)/i);
      if (match) {
        const colIdx = parseInt(match[1], 10) - 1;
        return columnColors[colIdx % columnColors.length];
      }
    }
    const idx = (typeof item === 'number') ? item : (item ? item.id || 0 : 0);
    return columnColors[Math.floor(idx / 28) % columnColors.length];
  }

  // Coordinate transforms
  function canvasToWorld(cx, cy) {
    return {
      x: meta.mc_transform.scale_x * cx + meta.mc_transform.offset_x,
      z: meta.mc_transform.scale_z * cy + meta.mc_transform.offset_z
    };
  }

  function worldToCanvas(wx, wz) {
    return {
      x: (wx - meta.mc_transform.offset_x) / meta.mc_transform.scale_x,
      y: (wz - meta.mc_transform.offset_z) / meta.mc_transform.scale_z
    };
  }

  function screenToCanvas(sx, sy) {
    const rect = canvas.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    const px = (sx - rect.left) * dpr;
    const py = (sy - rect.top) * dpr;
    const w = canvas.width;
    const h = canvas.height;
    return {
      x: (px - w / 2) / camera.zoom + camera.x,
      y: (py - h / 2) / camera.zoom + camera.y
    };
  }

  function canvasToScreen(cx, cy) {
    const w = canvas.width;
    const h = canvas.height;
    const dpr = window.devicePixelRatio || 1;
    return {
      x: ((cx - camera.x) * camera.zoom + w / 2) / dpr,
      y: ((cy - camera.y) * camera.zoom + h / 2) / dpr
    };
  }

  // Resize handling
  function resizeCanvas() {
    const dpr = window.devicePixelRatio || 1;
    canvas.width = window.innerWidth * dpr;
    canvas.height = window.innerHeight * dpr;
    requestRender();
  }
  window.addEventListener('resize', resizeCanvas);

  // Initial fit
  function fitToScreen() {
    const dpr = window.devicePixelRatio || 1;
    const scaleX = (window.innerWidth * dpr) / meta.width;
    const scaleY = (window.innerHeight * dpr) / meta.height;
    const zoom = Math.min(scaleX, scaleY) * 0.95;
    camera.targetX = meta.width / 2;
    camera.targetY = meta.height / 2;
    camera.targetZoom = zoom;
  }

  // Animation Loop
  let animId = null;
  function animate() {
    const lerp = 0.2;
    let changed = false;

    if (Math.abs(camera.x - camera.targetX) > 0.05) {
      camera.x += (camera.targetX - camera.x) * lerp;
      changed = true;
    } else {
      camera.x = camera.targetX;
    }

    if (Math.abs(camera.y - camera.targetY) > 0.05) {
      camera.y += (camera.targetY - camera.y) * lerp;
      changed = true;
    } else {
      camera.y = camera.targetY;
    }

    if (Math.abs(camera.zoom - camera.targetZoom) > 0.0001) {
      camera.zoom += (camera.targetZoom - camera.zoom) * lerp;
      changed = true;
    } else {
      camera.zoom = camera.targetZoom;
    }

    render();
    updateTelemetry();

    if (changed || isDragging) {
      requestAnimationFrame(animate);
    } else {
      animId = null;
    }
  }

  function requestRender() {
    if (!animId) {
      animId = requestAnimationFrame(animate);
    }
  }

  // Main Render function
  function render() {
    const dpr = window.devicePixelRatio || 1;
    const cw = canvas.width;
    const ch = canvas.height;

    ctx.save();
    ctx.clearRect(0, 0, cw, ch);

    // Apply smoothing setting (Nearest-neighbor for crisp Minecraft pixel font when zoomed in)
    const isCrispZoom = camera.zoom >= 0.8;
    ctx.imageSmoothingEnabled = !pixelArtMode && !isCrispZoom;
    if (pixelArtMode || isCrispZoom) {
      ctx.imageSmoothingQuality = 'low';
    } else {
      ctx.imageSmoothingQuality = 'medium';
    }

    // Viewport transform
    ctx.translate(cw / 2, ch / 2);
    ctx.scale(camera.zoom, camera.zoom);
    ctx.translate(-camera.x, -camera.y);

    // Draw Base Map Image
    if (activeImage) {
      ctx.drawImage(activeImage, 0, 0, meta.width, meta.height);
    }

    // Dynamic Full-Resolution Slippy Tile Streamer when zoomed in
    if (camera.zoom > 0.15) {
      const halfW = cw / (2 * camera.zoom);
      const halfH = ch / (2 * camera.zoom);
      const viewLeft = camera.x - halfW;
      const viewRight = camera.x + halfW;
      const viewTop = camera.y - halfH;
      const viewBottom = camera.y + halfH;

      const startCol = Math.max(0, Math.floor(viewLeft / TILE_SIZE));
      const endCol = Math.min(numCols - 1, Math.floor(viewRight / TILE_SIZE));
      const startRow = Math.max(0, Math.floor(viewTop / TILE_SIZE));
      const endRow = Math.min(numRows - 1, Math.floor(viewBottom / TILE_SIZE));

      for (let r = startRow; r <= endRow; r++) {
        for (let c = startCol; c <= endCol; c++) {
          const tile = getTile(r, c);
          if (tile && tile.complete && tile.naturalWidth > 0) {
            ctx.drawImage(tile, c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
          }
        }
      }
    }

    // Draw Screenshot Overlays if enabled
    if (showScreenshotGrid && meta.images) {
      ctx.lineWidth = Math.max(2, 2 / camera.zoom);
      for (const img of meta.images) {
        const isHovered = (hoveredImage && hoveredImage.id === img.id);
        const colColor = getScreenshotColor(img);

        ctx.strokeStyle = isHovered ? '#ffffff' : colColor;
        ctx.fillStyle = isHovered ? 'rgba(255, 255, 255, 0.15)' : 'rgba(0, 0, 0, 0.05)';

        ctx.strokeRect(img.x, img.y, img.width, img.height);
        ctx.fillRect(img.x, img.y, img.width, img.height);

        // Draw Badge
        const badgeSize = Math.max(16, 24 / Math.sqrt(camera.zoom));
        const fontSize = Math.max(12, 16 / Math.sqrt(camera.zoom));
        ctx.fillStyle = colColor;
        ctx.fillRect(img.x + 10, img.y + 10, fontSize * 3.2, fontSize * 1.5);
        ctx.fillStyle = '#000000';
        ctx.font = `bold ${fontSize}px sans-serif`;
        ctx.fillText(`#${img.id.toString().padStart(2, '0')}`, img.x + 16, img.y + 10 + fontSize * 1.15);
      }
    }

    // Draw POIs
    if (showPois && meta.pois) {
      for (const poi of meta.pois) {
        drawPoiMarker(poi, hoveredPoi === poi);
      }
    }

    // Draw User Waypoints
    for (const wp of userWaypoints) {
      drawWaypointMarker(wp);
    }

    // Draw Live Synced Players from Minecraft Mod
    for (const uuid in livePlayers) {
      drawLivePlayer(livePlayers[uuid]);
    }

    // Draw Measurement line
    if (measurePoints.length > 0) {
      ctx.strokeStyle = '#f56565';
      ctx.lineWidth = Math.max(3, 3 / camera.zoom);
      ctx.beginPath();
      ctx.moveTo(measurePoints[0].x, measurePoints[0].y);
      for (let i = 1; i < measurePoints.length; i++) {
        ctx.lineTo(measurePoints[i].x, measurePoints[i].y);
      }
      ctx.stroke();

      for (const pt of measurePoints) {
        ctx.fillStyle = '#f56565';
        ctx.beginPath();
        ctx.arc(pt.x, pt.y, Math.max(6, 6 / camera.zoom), 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = Math.max(2, 2 / camera.zoom);
        ctx.stroke();
      }
    }

    ctx.restore();
    renderMinimap();
  }

  function drawPoiMarker(poi, isHovered) {
    const radius = isHovered ? Math.max(12, 14 / camera.zoom) : Math.max(8, 9 / camera.zoom);
    let color = '#ecc94b';
    if (poi.category === 'dungeon') color = '#9f7aea';
    if (poi.category === 'origin') color = '#48bb78';

    ctx.save();
    ctx.shadowColor = color;
    ctx.shadowBlur = isHovered ? 15 : 6;
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(poi.x, poi.y, radius, 0, Math.PI * 2);
    ctx.fill();

    ctx.lineWidth = Math.max(2, 2 / camera.zoom);
    ctx.strokeStyle = '#ffffff';
    ctx.stroke();

    if (camera.zoom > 0.4 || isHovered) {
      const fontSize = Math.max(11, 13 / camera.zoom);
      ctx.font = `600 ${fontSize}px sans-serif`;
      ctx.fillStyle = '#ffffff';
      ctx.shadowColor = 'rgba(0, 0, 0, 0.9)';
      ctx.shadowBlur = 4;
      ctx.fillText(poi.name, poi.x + radius + 4, poi.y + fontSize / 3);
    }
    ctx.restore();
  }

  function drawWaypointMarker(wp) {
    const radius = Math.max(9, 10 / camera.zoom);
    ctx.save();
    ctx.shadowColor = wp.color || '#4299e1';
    ctx.shadowBlur = 8;
    ctx.fillStyle = wp.color || '#4299e1';
    ctx.beginPath();
    ctx.arc(wp.x, wp.y, radius, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = Math.max(2, 2 / camera.zoom);
    ctx.stroke();

    const fontSize = Math.max(11, 13 / camera.zoom);
    ctx.font = `600 ${fontSize}px sans-serif`;
    ctx.fillStyle = '#ffffff';
    ctx.shadowColor = 'rgba(0,0,0,0.9)';
    ctx.shadowBlur = 4;
    ctx.fillText(wp.name, wp.x + radius + 4, wp.y + fontSize / 3);
    ctx.restore();
  }

  // Draw Live Synced Player Marker
  function drawLivePlayer(p) {
    const pt = worldToCanvas(p.x, p.z);
    const radius = Math.max(10, 12 / camera.zoom);

    ctx.save();
    ctx.shadowColor = '#00e5ff';
    ctx.shadowBlur = 12;

    // Outer glow ring
    ctx.fillStyle = '#00e5ff';
    ctx.beginPath();
    ctx.arc(pt.x, pt.y, radius, 0, Math.PI * 2);
    ctx.fill();

    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = Math.max(2, 2.5 / camera.zoom);
    ctx.stroke();

    // Direction pointer
    if (typeof p.yaw === 'number') {
      const rad = (p.yaw - 90) * (Math.PI / 180);
      const tipDist = radius * 1.6;
      ctx.beginPath();
      ctx.moveTo(pt.x, pt.y);
      ctx.lineTo(pt.x + Math.cos(rad) * tipDist, pt.y + Math.sin(rad) * tipDist);
      ctx.strokeStyle = '#ff0055';
      ctx.lineWidth = Math.max(2, 3 / camera.zoom);
      ctx.stroke();
    }

    // Name tag & coords
    const fontSize = Math.max(12, 14 / camera.zoom);
    ctx.font = `bold ${fontSize}px sans-serif`;
    ctx.fillStyle = '#ffffff';
    ctx.shadowColor = 'rgba(0,0,0,0.9)';
    ctx.shadowBlur = 4;
    const label = `${p.name} (${Math.round(p.x)}, ${Math.round(p.y)}, ${Math.round(p.z)})`;
    ctx.fillText(label, pt.x + radius + 6, pt.y + fontSize / 3);

    ctx.restore();
  }

  // Live WebSocket Sync Client for Web Map
  let livePlayers = {};
  let syncSocket = null;

  function initLiveSync() {
    const host = window.location.hostname || 'localhost';
    const wsUrl = `ws://${host}:8080`;
    const liveEl = document.getElementById('live-count');

    try {
      syncSocket = new WebSocket(wsUrl);

      syncSocket.onopen = () => {
        if (liveEl) {
          liveEl.textContent = '0 Online';
          liveEl.style.color = '#48bb78';
        }
        syncSocket.send(JSON.stringify({ type: 'join', room: 'default', role: 'viewer', name: 'Web-Viewer' }));
      };

      syncSocket.onmessage = (evt) => {
        try {
          const data = JSON.parse(evt.data);
          if (data.type === 'init' || data.type === 'sync') {
            livePlayers = {};
            (data.players || []).forEach(p => { livePlayers[p.uuid] = p; });
          } else if (data.type === 'update' && data.player) {
            livePlayers[data.player.uuid] = data.player;
          } else if (data.type === 'leave') {
            delete livePlayers[data.uuid];
          }
          if (liveEl) {
            const count = Object.keys(livePlayers).length;
            liveEl.textContent = `${count} Online`;
            liveEl.style.color = count > 0 ? '#48bb78' : '#a0aec0';
          }
          requestRender();
        } catch (e) {}
      };

      syncSocket.onclose = () => {
        if (liveEl) {
          liveEl.textContent = 'Offline';
          liveEl.style.color = '#a0aec0';
        }
        setTimeout(initLiveSync, 4000);
      };

      syncSocket.onerror = () => {
        if (syncSocket) syncSocket.close();
      };
    } catch (e) {
      if (liveEl) {
        liveEl.textContent = 'Offline';
        liveEl.style.color = '#a0aec0';
      }
      setTimeout(initLiveSync, 5000);
    }
  }

  // Start live sync in background
  initLiveSync();

  // Minimap Render
  function renderMinimap() {
    const mw = minimapCanvas.width;
    const mh = minimapCanvas.height;
    minimapCtx.clearRect(0, 0, mw, mh);

    if (activeImage) {
      minimapCtx.drawImage(activeImage, 0, 0, mw, mh);
    }

    // Draw Viewport Rectangle
    const tl = screenToCanvas(0, 0);
    const br = screenToCanvas(window.innerWidth, window.innerHeight);

    const mx1 = (tl.x / meta.width) * mw;
    const my1 = (tl.y / meta.height) * mh;
    const mx2 = (br.x / meta.width) * mw;
    const my2 = (br.y / meta.height) * mh;

    minimapCtx.strokeStyle = '#48bb78';
    minimapCtx.lineWidth = 1.5;
    minimapCtx.fillStyle = 'rgba(72, 187, 120, 0.2)';
    minimapCtx.fillRect(mx1, my1, mx2 - mx1, my2 - my1);
    minimapCtx.strokeRect(mx1, my1, mx2 - mx1, my2 - my1);
  }

  // Telemetry updates
  const hudX = document.getElementById('hud-x');
  const hudZ = document.getElementById('hud-z');
  const hudZoom = document.getElementById('hud-zoom');
  const hudPx = document.getElementById('hud-px');

  function updateTelemetry(cx, cy) {
    if (hudZoom) {
      hudZoom.textContent = `${Math.round(camera.zoom * 100)}%`;
    }
    if (cx !== undefined && cy !== undefined) {
      const world = canvasToWorld(cx, cy);
      if (hudX) hudX.textContent = Math.round(world.x);
      if (hudZ) hudZ.textContent = Math.round(world.z);
      if (hudPx) hudPx.textContent = `${Math.round(cx)}, ${Math.round(cy)}`;
    }
  }

  // Pointer & Interaction Handling
  canvas.addEventListener('mousedown', (e) => {
    if (e.button !== 0) return;
    const pos = screenToCanvas(e.clientX, e.clientY);

    if (measureMode) {
      measurePoints.push(pos);
      if (measurePoints.length > 2) measurePoints = [pos];
      updateMeasurement();
      requestRender();
      return;
    }

    isDragging = true;
    dragStart = { x: e.clientX, y: e.clientY };
    cameraDragStart = { x: camera.targetX, y: camera.targetY };
  });

  window.addEventListener('mousemove', (e) => {
    const pos = screenToCanvas(e.clientX, e.clientY);
    updateTelemetry(pos.x, pos.y);

    if (isDragging) {
      const dpr = window.devicePixelRatio || 1;
      const dx = (e.clientX - dragStart.x) * dpr;
      const dy = (e.clientY - dragStart.y) * dpr;
      camera.targetX = cameraDragStart.x - dx / camera.zoom;
      camera.targetY = cameraDragStart.y - dy / camera.zoom;
      requestRender();
      return;
    }

    // Check hovered screenshot
    if (showScreenshotGrid && meta.images) {
      let found = null;
      for (let i = meta.images.length - 1; i >= 0; i--) {
        const img = meta.images[i];
        if (pos.x >= img.x && pos.x <= img.x + img.width &&
            pos.y >= img.y && pos.y <= img.y + img.height) {
          found = img;
          break;
        }
      }
      if (found !== hoveredImage) {
        hoveredImage = found;
        updateTooltip(e.clientX, e.clientY, found);
        requestRender();
      } else if (found) {
        updateTooltip(e.clientX, e.clientY, found);
      } else {
        hideTooltip();
      }
    }
  });

  window.addEventListener('mouseup', () => {
    isDragging = false;
  });

  // Wheel Zoom anchored to cursor
  canvas.addEventListener('wheel', (e) => {
    e.preventDefault();
    const zoomFactor = e.deltaY < 0 ? 1.25 : 0.8;
    const minZoom = 0.05;
    const maxZoom = 12.0;
    const newZoom = Math.max(minZoom, Math.min(maxZoom, camera.targetZoom * zoomFactor));

    const mousePos = screenToCanvas(e.clientX, e.clientY);
    camera.targetX = mousePos.x + (camera.targetX - mousePos.x) * (camera.targetZoom / newZoom);
    camera.targetY = mousePos.y + (camera.targetY - mousePos.y) * (camera.targetZoom / newZoom);
    camera.targetZoom = newZoom;

    requestRender();
  }, { passive: false });

  // Double click to zoom in
  canvas.addEventListener('dblclick', (e) => {
    const mousePos = screenToCanvas(e.clientX, e.clientY);
    camera.targetX = mousePos.x;
    camera.targetY = mousePos.y;
    camera.targetZoom = Math.min(10.0, camera.targetZoom * 2.0);
    requestRender();
  });

  // Minimap click navigation
  minimapCanvas.addEventListener('click', (e) => {
    const rect = minimapCanvas.getBoundingClientRect();
    const mx = (e.clientX - rect.left) / rect.width;
    const my = (e.clientY - rect.top) / rect.height;
    camera.targetX = mx * meta.width;
    camera.targetY = my * meta.height;
    requestRender();
  });

  // Tooltip
  const tooltip = document.getElementById('canvas-tooltip');
  function updateTooltip(sx, sy, img) {
    if (!tooltip || !img) return;
    tooltip.style.display = 'block';
    tooltip.style.left = `${sx + 15}px`;
    tooltip.style.top = `${sy + 15}px`;
    tooltip.innerHTML = `
      <div style="font-weight:700; color:#63b3ed; margin-bottom:4px;">Screenshot #${img.id}</div>
      <div style="font-family:monospace; font-size:11px; color:#cbd5e0;">${img.filename}</div>
      <div style="font-size:11px; color:#a0aec0; margin-top:4px;">
        Minecraft bounds:<br>
        X: [${img.mc_bounds.min_x} .. ${img.mc_bounds.max_x}]<br>
        Z: [${img.mc_bounds.min_z} .. ${img.mc_bounds.max_z}]
      </div>
      <div style="margin-top:6px; font-size:10px; color:#68d391;">Click screenshot card in drawer to view full original</div>
    `;
  }

  function hideTooltip() {
    if (tooltip) tooltip.style.display = 'none';
  }

  // Measurement calculation
  function updateMeasurement() {
    const panel = document.getElementById('measure-info');
    if (!panel) return;
    if (measurePoints.length === 2) {
      const p1 = canvasToWorld(measurePoints[0].x, measurePoints[0].y);
      const p2 = canvasToWorld(measurePoints[1].x, measurePoints[1].y);
      const dx = p2.x - p1.x;
      const dz = p2.z - p1.z;
      const dist = Math.round(Math.sqrt(dx * dx + dz * dz));
      const manhattan = Math.round(Math.abs(dx) + Math.abs(dz));
      const walkSecs = Math.round(dist / 4.3);
      const sprintSecs = Math.round(dist / 5.6);

      panel.style.display = 'block';
      panel.innerHTML = `
        <strong>📏 Measurement:</strong><br>
        Direct: <span style="color:#68d391">${dist} blocks</span><br>
        Manhattan (walking paths): ${manhattan} blocks<br>
        Walk time: ~${walkSecs}s | Sprint: ~${sprintSecs}s
      `;
    } else {
      panel.style.display = 'none';
    }
  }

  // Teleport to coordinates
  window.teleportTo = function(wx, wz) {
    const canvasPos = worldToCanvas(wx, wz);
    camera.targetX = canvasPos.x;
    camera.targetY = canvasPos.y;
    camera.targetZoom = Math.max(camera.targetZoom, 1.0);
    requestRender();
  };

  // Zoom controls
  document.getElementById('btn-zoom-in')?.addEventListener('click', () => {
    camera.targetZoom = Math.min(12.0, camera.targetZoom * 1.5);
    requestRender();
  });
  document.getElementById('btn-zoom-out')?.addEventListener('click', () => {
    camera.targetZoom = Math.max(0.05, camera.targetZoom / 1.5);
    requestRender();
  });
  document.getElementById('btn-reset')?.addEventListener('click', () => {
    fitToScreen();
    requestRender();
  });
  document.getElementById('btn-grid')?.addEventListener('click', (e) => {
    showScreenshotGrid = !showScreenshotGrid;
    e.currentTarget.classList.toggle('active', showScreenshotGrid);
    if (!showScreenshotGrid) hideTooltip();
    requestRender();
  });
  document.getElementById('btn-pois')?.addEventListener('click', (e) => {
    showPois = !showPois;
    e.currentTarget.classList.toggle('active', showPois);
    requestRender();
  });
  document.getElementById('btn-pixel')?.addEventListener('click', (e) => {
    pixelArtMode = !pixelArtMode;
    e.currentTarget.classList.toggle('active', pixelArtMode);
    requestRender();
  });
  document.getElementById('btn-measure')?.addEventListener('click', (e) => {
    measureMode = !measureMode;
    measurePoints = [];
    e.currentTarget.classList.toggle('active', measureMode);
    canvas.classList.toggle('measuring', measureMode);
    updateMeasurement();
    requestRender();
  });

  // Fullscreen
  document.getElementById('btn-fullscreen')?.addEventListener('click', () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen();
    } else {
      document.exitFullscreen();
    }
  });

  // Sidebar Drawer
  const sidebar = document.getElementById('sidebar');
  document.getElementById('btn-sidebar')?.addEventListener('click', () => {
    sidebar.classList.toggle('open');
  });
  document.getElementById('btn-close-sidebar')?.addEventListener('click', () => {
    sidebar.classList.remove('open');
  });

  // Sidebar Tabs
  const tabBtns = document.querySelectorAll('.tab-btn');
  tabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      tabBtns.forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
      btn.classList.add('active');
      const tabId = btn.getAttribute('data-tab');
      document.getElementById(`tab-${tabId}`)?.classList.add('active');
    });
  });

  // Populate Screenshots List in Sidebar
  const screenshotList = document.getElementById('screenshot-list');
  if (screenshotList && meta.images) {
    meta.images.forEach(img => {
      const card = document.createElement('div');
      card.className = 'item-card';
      const colColor = getScreenshotColor(img);
      const minX = img.mc_bounds ? img.mc_bounds.min_x : Math.round(canvasToWorld(img.x, img.y).x);
      const maxX = img.mc_bounds ? img.mc_bounds.max_x : Math.round(canvasToWorld(img.x + img.width, img.y + img.height).x);
      const minZ = img.mc_bounds ? img.mc_bounds.min_z : Math.round(canvasToWorld(img.x, img.y).z);
      const maxZ = img.mc_bounds ? img.mc_bounds.max_z : Math.round(canvasToWorld(img.x + img.width, img.y + img.height).z);
      card.innerHTML = `
        <div class="item-header">
          <span class="item-title" style="color:${colColor}">#${img.id.toString().padStart(2, '0')}: ${img.filename}</span>
          <span class="badge-tag">Inspect</span>
        </div>
        <div class="item-sub">
          X: [${minX} .. ${maxX}] | Z: [${minZ} .. ${maxZ}]
        </div>
      `;
      card.addEventListener('click', () => {
        // Zoom to screenshot
        camera.targetX = img.x + img.width / 2;
        camera.targetY = img.y + img.height / 2;
        camera.targetZoom = 0.8;
        requestRender();

        // Open Lightbox
        const imgPath = img.filename.startsWith('map_col') ? `new_screenshots/${img.filename}` : `images/${img.filename}`;
        openLightbox(imgPath, `Screenshot #${img.id}: ${img.filename}`);
      });
      screenshotList.appendChild(card);
    });
  }

  // Populate POI List in Sidebar
  const poiList = document.getElementById('poi-list');
  if (poiList && meta.pois) {
    meta.pois.slice(0, 40).forEach(poi => {
      const card = document.createElement('div');
      card.className = 'item-card';
      card.innerHTML = `
        <div class="item-header">
          <span class="item-title">${poi.name}</span>
          <span class="badge-tag">${poi.category}</span>
        </div>
        <div class="item-sub">Coordinates: X: ${poi.mc_x}, Z: ${poi.mc_z}</div>
      `;
      card.addEventListener('click', () => {
        window.teleportTo(poi.mc_x, poi.mc_z);
      });
      poiList.appendChild(card);
    });
  }

  // Lightbox Modal
  const lightbox = document.getElementById('lightbox-modal');
  const lightboxImg = document.getElementById('lightbox-img');
  const lightboxTitle = document.getElementById('lightbox-title');
  function openLightbox(src, title) {
    if (!lightbox) return;
    lightboxImg.src = src;
    lightboxTitle.textContent = title;
    lightbox.classList.add('open');
  }
  document.getElementById('lightbox-close')?.addEventListener('click', () => {
    lightbox.classList.remove('open');
  });
  lightbox?.addEventListener('click', (e) => {
    if (e.target === lightbox) lightbox.classList.remove('open');
  });

  // Coordinate Search Modal
  const searchModal = document.getElementById('search-modal');
  document.getElementById('btn-search')?.addEventListener('click', () => {
    searchModal.classList.toggle('open');
  });
  document.getElementById('btn-search-go')?.addEventListener('click', () => {
    const x = parseFloat(document.getElementById('input-mc-x').value);
    const z = parseFloat(document.getElementById('input-mc-z').value);
    if (!isNaN(x) && !isNaN(z)) {
      window.teleportTo(x, z);
      searchModal.classList.remove('open');
    }
  });

  // Keyboard Shortcuts
  window.addEventListener('keydown', (e) => {
    if (e.target.tagName === 'INPUT') return;
    if (e.key === 'f' || e.key === 'F') {
      document.getElementById('btn-fullscreen')?.click();
    } else if (e.key === ' ' || e.key === 'r' || e.key === 'R') {
      fitToScreen();
      requestRender();
    } else if (e.key === 'g' || e.key === 'G') {
      document.getElementById('btn-grid')?.click();
    } else if (e.key === 'm' || e.key === 'M') {
      document.getElementById('btn-measure')?.click();
    } else if (e.key === '+' || e.key === '=') {
      camera.targetZoom = Math.min(12.0, camera.targetZoom * 1.4);
      requestRender();
    } else if (e.key === '-' || e.key === '_') {
      camera.targetZoom = Math.max(0.05, camera.targetZoom / 1.4);
      requestRender();
    } else if (e.key === 'Escape') {
      searchModal.classList.remove('open');
      lightbox.classList.remove('open');
      sidebar.classList.remove('open');
      if (measureMode) document.getElementById('btn-measure')?.click();
    }
  });

  // Progressive Image Loading
  const statusDot = document.getElementById('status-dot');
  const statusText = document.getElementById('status-text');

  previewImg.onload = () => {
    if (!fullImageLoaded) {
      activeImage = previewImg;
      fitToScreen();
      requestRender();
    }
  };
  previewImg.src = 'stitched_map_2k.jpg';

  fullImg.onload = () => {
    activeImage = fullImg;
    fullImageLoaded = true;
    if (statusDot) statusDot.classList.remove('loading');
    if (statusText) statusText.textContent = `FTB Native Block Map Ready (${meta.width} × ${meta.height} px, 27.9 MB)`;
    requestRender();
  };
  fullImg.src = 'map_native.webp';

  // Dynamic UI Header & Tab Updates
  const badgeTag = document.querySelector('.brand .badge-tag');
  if (badgeTag && meta.images) {
    badgeTag.textContent = `${meta.images.length} Screenshots Stitched`;
  }
  const btnGrid = document.getElementById('btn-grid');
  if (btnGrid && meta.images) {
    btnGrid.title = `Show/Hide Screenshot Placement Outlines (${meta.images.length} images)`;
  }
  const tabScreenshots = document.querySelector('.tab-btn[data-tab="screenshots"]');
  if (tabScreenshots && meta.images) {
    tabScreenshots.textContent = `Screenshots (${meta.images.length})`;
  }
  const tabPois = document.querySelector('.tab-btn[data-tab="pois"]');
  if (tabPois && meta.pois) {
    tabPois.textContent = `Structures (${meta.pois.length})`;
  }

  // Init
  resizeCanvas();
  fitToScreen();
})();
