/* Jetpack Jung LAN lobby + player-state relay client. */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const hostBtn = $('lanHostBtn'), joinBtn = $('lanJoinBtn');
  const status = $('lanStatus'), info = $('lanRoomInfo');
  if (!hostBtn || !joinBtn) return;
  let socket = null, hosting = false, roomUrl = '', roomCode = '', joined = false;

  function say(message, kind) {
    status.textContent = message;
    status.className = 'lan-status' + (kind ? ' ' + kind : '');
  }
  function showRoom(extra) {
    info.classList.remove('hidden');
    info.innerHTML = '<b>LAN ROOM</b><br>' +
      (hosting ? 'Host: This phone' : 'Host: ' + escapeHtml($('lanAddress').value.trim())) +
      '<br><b>Room code:</b> ' + escapeHtml(roomCode) +
      '<br><b>Players:</b> ' + (extra || (joined ? '2–10 connected' : '1 / 10')) +
      '<br><small>All phones must stay on the same hotspot / Wi-Fi.</small>';
  }
  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }
  function closeSocket() {
    if (socket) { try { socket.close(); } catch (_) {} }
    socket = null; joined = false;
  }
  function connect(url, code, isHost) {
    closeSocket();
    hosting = isHost; roomCode = code;
    if (!/^ws:\/\/[\d.]+:\d{2,5}$/.test(url)) {
      say('Enter the host phone IPv4 address, for example 192.168.43.1', 'err'); return;
    }
    say('Connecting to LAN room…');
    try { socket = new WebSocket(url); }
    catch (e) { say('Could not open LAN connection: ' + e.message, 'err'); return; }
    socket.onopen = function () {
      socket.send(JSON.stringify({type:'join', code:roomCode, name:(document.getElementById('nameInput')?.value || 'Player')}));
    };
    socket.onmessage = function (event) {
      let msg; try { msg = JSON.parse(event.data); } catch (_) { return; }
      if (msg.type === 'hello') return;
      if ((msg.type === 'state' || msg.type === 'action' || msg.type === 'match_start' || msg.type === 'match_state' || msg.type === 'match_end') && window.JetpackGameNet) window.JetpackGameNet.receive(msg);
      if (msg.type === 'welcome') {
        joined = true;
        say('Connected! Waiting for the host to start the match.', 'ok');
        showRoom((msg.players || 2) + ' / 10');
      } else if (msg.type === 'peer_count') {
        showRoom((msg.players || 1) + ' / 10');
      } else if (msg.type === 'peer_joined') {
        say((msg.name || 'A player') + ' joined the room.', 'ok');
        showRoom((msg.players || 2) + ' / 10');
      } else if (msg.type === 'peer_left') {
        if (window.JetpackGameNet) window.JetpackGameNet.removeById(msg.id, msg.name);
        say((msg.name || 'A player') + ' left the room.');
      } else if (msg.type === 'error') {
        say(msg.message || 'LAN connection rejected.', 'err');
      }
    };
    socket.onerror = function () { say('Connection failed. Check same Wi-Fi/hotspot, host IP and room code.', 'err'); };
    socket.onclose = function () { if (joined) say('Disconnected from LAN room.'); joined = false; };
  }

  hostBtn.addEventListener('click', function () {
    if (!window.JetpackLAN || !JetpackLAN.startHost) {
      say('LAN hosting is available in the Android APK build only.', 'err'); return;
    }
    const result = JetpackLAN.startHost();
    if (!result || result.indexOf('ERROR:') === 0) {
      say('Could not start host: ' + (result || 'unknown error'), 'err'); return;
    }
    roomUrl = result;
    roomCode = JetpackLAN.getRoomCode ? JetpackLAN.getRoomCode() : '';
    const ip = result.replace(/^ws:\/\//, '').replace(/:\d+$/, '');
    $('lanAddress').value = ip;
    $('lanCode').value = roomCode;
    hosting = true;
    info.classList.remove('hidden');
    info.innerHTML = '<b>HOST READY</b><br>Address: ' + escapeHtml(ip) +
      '<br><b>Room code:</b> ' + escapeHtml(roomCode) +
      '<br><b>Players:</b> 1 / 10<br><small>Share this IP and code with up to 9 friends.</small>';
    say('Room created on this phone. Other Android phones can join this hotspot.', 'ok');
    if (window.__jjHostPoll) clearInterval(window.__jjHostPoll);
    if (window.__jjHostPacketPoll) clearInterval(window.__jjHostPacketPoll);
    window.__jjHostPacketPoll = setInterval(function () {
      if (!window.JetpackLAN || !JetpackLAN.pollHostPacket || !window.JetpackGameNet) return;
      let raw; while ((raw = JetpackLAN.pollHostPacket())) { try { const msg = JSON.parse(raw); if (msg.type === 'peer_left') JetpackGameNet.removeById(msg.id, msg.name); else JetpackGameNet.receive(msg); } catch (_) {} }
    }, 40);
    window.__jjHostPoll = setInterval(function () {
      if (!hosting || !window.JetpackLAN || !JetpackLAN.getPlayerCount) return;
      const count = JetpackLAN.getPlayerCount();
      info.innerHTML = '<b>HOST READY</b><br>Address: ' + escapeHtml(ip) +
        '<br><b>Room code:</b> ' + escapeHtml(roomCode) +
        '<br><b>Players:</b> ' + count + ' / 10<br><small>Share this IP and code with up to 9 friends.</small>';
    }, 1000);
  });

  joinBtn.addEventListener('click', function () {
    const ip = $('lanAddress').value.trim();
    const code = $('lanCode').value.trim().toUpperCase();
    if (!ip || !code) { say('Enter the host IP and room code.', 'err'); return; }
    connect('ws://' + ip + ':28765', code, false);
  });

  window.addEventListener('beforeunload', closeSocket);
  window.JetpackLanLobby = {
    isConnected: () => joined || hosting,
    isHost: () => hosting,
    isClient: () => joined && !hosting,
    send: packet => { const raw = JSON.stringify(packet); if (hosting && window.JetpackLAN && JetpackLAN.broadcastPacket) JetpackLAN.broadcastPacket(raw); else if (socket && socket.readyState === WebSocket.OPEN) socket.send(raw); },
    disconnect: closeSocket
  };
})();
