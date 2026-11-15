/*
 * 르르르 상태 위젯 애니메이션 (rereureu_A_발전모드.html + rereureu_B_EV모드.html 통합본)
 *
 *   const w = RrrWidget.mount(el, { mode: 'ev' | 'gen', motion: 'normal' | 'calm' | 'off' });
 *   w.setMode('gen');      // 두 레이어를 크로스페이드로 전환
 *   w.setMotion('calm');   // normal = 원본 속도, calm = 느리게, off = 정지 이미지
 *
 * ※ 이 파일은 pleos_connect(차량 화면 시뮬레이션)와 르르링 앱(위젯 설정 미리보기)에 같은 내용으로 들어 있습니다.
 */
(function () {
  'use strict';

  var CSS = [
    '.rw{position:relative}', /* 크기는 마운트하는 쪽에서 지정 */
    '.rw .rw-layer{position:absolute;inset:0;transition:opacity .7s ease}',
    '.rw .rw-layer.rw-hidden{opacity:0}',
    '.rw svg{width:100%;height:100%;overflow:visible;display:block}',

    /* 공통 키프레임 */
    '@keyframes rwAntL{0%,100%{transform:rotate(0)}50%{transform:rotate(-5deg)}}',
    '@keyframes rwAntR{0%,100%{transform:rotate(0)}50%{transform:rotate(5deg)}}',
    '@keyframes rwDash{to{stroke-dashoffset:-16}}',

    /* ── A. 발전 모드 ── */
    '.rw-gen .antenna-l{transform-origin:127px 92px;animation:rwAntL 1.3s ease-in-out infinite}',
    '.rw-gen .antenna-r{transform-origin:183px 92px;animation:rwAntR 1.3s ease-in-out infinite}',
    '@keyframes rwSweat{0%{opacity:0;transform:translateY(-2px) scale(.85)}30%{opacity:.95;transform:translateY(0) scale(1)}80%{opacity:.9;transform:translateY(4px) scale(1)}100%{opacity:0;transform:translateY(6px) scale(.85)}}',
    '.rw-gen .sweat-drop{transform-origin:184px 104px;animation:rwSweat 2.6s ease-in-out infinite}',
    '@keyframes rwCheek{0%,100%{opacity:.55}50%{opacity:.85}}',
    '.rw-gen .cheeks{animation:rwCheek 1.3s ease-in-out infinite}',
    '@keyframes rwFuel1{0%{opacity:0;transform:translate(0,0) scale(.8)}20%{opacity:1;transform:translate(16px,2px) scale(1)}75%{opacity:1;transform:translate(46px,6px) scale(.95)}95%{opacity:0;transform:translate(54px,8px) scale(.6)}100%{opacity:0;transform:translate(0,0) scale(.8)}}',
    '@keyframes rwFuel2{0%,40%{opacity:0;transform:translate(0,0) scale(.8)}55%{opacity:1;transform:translate(18px,-1px) scale(1)}88%{opacity:1;transform:translate(46px,6px) scale(.95)}100%{opacity:0;transform:translate(54px,8px) scale(.6)}}',
    '.rw-gen .fuel-drop-1{animation:rwFuel1 2.6s ease-in-out infinite}',
    '.rw-gen .fuel-drop-2{animation:rwFuel2 2.6s ease-in-out infinite}',
    '@keyframes rwBolt1{0%,15%{opacity:0;transform:translate(0,6px) scale(.6)}32%{opacity:1;transform:translate(4px,-8px) scale(1.05)}55%{opacity:1;transform:translate(26px,-6px) scale(1)}82%{opacity:1;transform:translate(54px,4px) scale(.92)}95%{opacity:0;transform:translate(62px,8px) scale(.65)}100%{opacity:0;transform:translate(0,6px) scale(.6)}}',
    '.rw-gen .energy-bolt-1{transform-origin:205px 158px;animation:rwBolt1 2.6s ease-in-out infinite}',
    '@keyframes rwBolt2{0%,45%{opacity:0;transform:translate(0,4px) scale(.5)}62%{opacity:1;transform:translate(-2px,-10px) scale(.95)}85%{opacity:1;transform:translate(12px,-12px) scale(.75)}100%{opacity:0;transform:translate(0,4px) scale(.5)}}',
    '.rw-gen .energy-bolt-2{transform-origin:196px 154px;animation:rwBolt2 2.6s ease-in-out infinite}',
    '@keyframes rwCharge{0%,45%{opacity:.22;transform:scaleY(.85)}72%,90%{opacity:1;transform:scaleY(1)}100%{opacity:.22;transform:scaleY(.85)}}',
    '.rw-gen .battery-bar-charging{transform-origin:280px 168px;animation:rwCharge 2.6s ease-in-out infinite}',
    '.rw-gen .flow-guide-line{animation:rwDash 1.3s linear infinite}',

    /* ── B. EV 모드 ── */
    '.rw-ev .antenna-l{transform-origin:127px 92px;animation:rwAntL 3.2s ease-in-out infinite}',
    '.rw-ev .antenna-r{transform-origin:183px 92px;animation:rwAntR 3.2s ease-in-out infinite}',
    '@keyframes rwCalm{0%,100%{opacity:1}50%{opacity:.8}}',
    '.rw-ev .battery-bar-charging{animation:rwCalm 3.2s ease-in-out infinite}',
    '@keyframes rwCoin{0%,55%{opacity:0;transform:translateY(6px) scale(.7)}68%{opacity:1;transform:translateY(0) scale(1)}85%{opacity:1;transform:translateY(-3px) scale(1)}100%{opacity:0;transform:translateY(-6px) scale(.9)}}',
    '.rw-ev .coin-sparkle{transform-box:fill-box;transform-origin:center;animation:rwCoin 3.2s ease-in-out infinite}',
    '@keyframes rwHalo{0%,100%{opacity:.18;transform:scale(1)}50%{opacity:.42;transform:scale(1.06)}}',
    '.rw-ev .battery-halo{filter:blur(5px);transform-box:fill-box;transform-origin:center;animation:rwHalo 3.2s ease-in-out infinite}',
    '@keyframes rwRise{0%{opacity:0;transform:translateY(0) scale(.6)}25%{opacity:.9}100%{opacity:0;transform:translateY(-26px) scale(1)}}',
    '.rw-ev .spark{transform-box:fill-box;transform-origin:center;animation:rwRise 3.2s ease-out infinite}',
    '.rw-ev .spark.s2{animation-delay:-1.1s}.rw-ev .spark.s3{animation-delay:-2.2s}',
    '@keyframes rwShell{0%,100%{stroke:#7FB4F5}50%{stroke:#2DD4AA}}',
    '.rw-ev .shell-swirl{animation:rwShell 3.2s ease-in-out infinite}'
  ].join('\n');

  // ───────── 공통 파츠 ─────────
  function arms() {
    return '' +
      '<g class="arms">' +
      ' <g class="left-arm">' +
      '  <line x1="114" y1="160" x2="92" y2="186" stroke="#1E1E1E" stroke-width="17" stroke-linecap="round"/>' +
      '  <circle class="hand" cx="92" cy="186" r="7.5" fill="#FDE4BE" stroke="#1E1E1E" stroke-width="3"/>' +
      '  <line x1="114" y1="160" x2="92" y2="186" stroke="#FDE4BE" stroke-width="11" stroke-linecap="round"/>' +
      ' </g>' +
      ' <g class="right-arm">' +
      '  <line x1="198" y1="160" x2="247" y2="178" stroke="#1E1E1E" stroke-width="17" stroke-linecap="round"/>' +
      '  <circle class="hand" cx="247" cy="178" r="7.5" fill="#FDE4BE" stroke="#1E1E1E" stroke-width="3"/>' +
      '  <line x1="198" y1="160" x2="247" y2="178" stroke="#FDE4BE" stroke-width="11" stroke-linecap="round"/>' +
      ' </g>' +
      '</g>';
  }

  function generator(opacity) {
    return '' +
      '<g class="mini-generator" opacity="' + opacity + '">' +
      ' <rect x="222" y="194" width="30" height="48" rx="6" fill="#64748B" stroke="#1E1E1E" stroke-width="2.8"/>' +
      ' <line x1="229" y1="210" x2="245" y2="210" stroke="#94A3B8" stroke-width="2.4" stroke-linecap="round"/>' +
      ' <line x1="229" y1="218" x2="245" y2="218" stroke="#94A3B8" stroke-width="2.4" stroke-linecap="round"/>' +
      ' <circle cx="237" cy="178" r="15" fill="#F8FAFC" stroke="#1E1E1E" stroke-width="3"/>' +
      ' <circle cx="237" cy="178" r="8" fill="#FDE68A" stroke="#F59E0B" stroke-width="2"/>' +
      ' <line class="crank-arm" x1="237" y1="178" x2="247" y2="178" stroke="#1E1E1E" stroke-width="3" stroke-linecap="round"/>' +
      ' <circle class="crank-knob" cx="247" cy="178" r="4" fill="#F5A623" stroke="#1E1E1E" stroke-width="2.4"/>' +
      '</g>';
  }

  function body(eyes, extra) {
    return '' +
      '<g class="rereureu-body">' +
      ' <g class="antenna-l"><path d="M 124 96 L 114 75 A 9 9 0 1 1 125 71 L 135 90" fill="#FDE4BE" stroke="#1E1E1E" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"/></g>' +
      ' <g class="antenna-r"><path d="M 176 90 L 186 71 A 9 9 0 1 1 197 75 L 187 96" fill="#FDE4BE" stroke="#1E1E1E" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"/></g>' +
      ' <path d="M 124 95 C 142 82 169 82 187 95 C 199 109 204 128 204 148 L 202 224 C 203 233 206 238 201 242 C 194 246 164 246 161 241 C 159 237 159 229 155 229 C 151 229 151 237 149 241 C 146 246 116 246 109 242 C 104 238 107 233 108 224 L 107 148 C 107 128 112 109 124 95 Z" fill="#FDE4BE" stroke="#1E1E1E" stroke-width="3.2" stroke-linejoin="round"/>' +
      ' <path d="M 108 145 L 126 160 L 121 169 L 106 155 Z" fill="#9BC9FF" stroke="#1E1E1E" stroke-width="3" stroke-linejoin="round"/>' +
      ' <path d="M 203 145 L 185 160 L 190 169 L 205 155 Z" fill="#9BC9FF" stroke="#1E1E1E" stroke-width="3" stroke-linejoin="round"/>' +
      ' <path d="M 107 196 L 122 190 L 124 200 L 107 207 Z" fill="#9BC9FF" stroke="#1E1E1E" stroke-width="3" stroke-linejoin="round"/>' +
      ' <path d="M 203 196 L 188 190 L 186 200 L 203 207 Z" fill="#9BC9FF" stroke="#1E1E1E" stroke-width="3" stroke-linejoin="round"/>' +
      ' <ellipse cx="155" cy="186" rx="40" ry="37" fill="#9BC9FF" stroke="#1E1E1E" stroke-width="3.2"/>' +
      ' <path class="shell-swirl" d="M 155 186 C 158 183 163 185 162 190 C 160 196 150 197 147 191 C 143 183 151 174 160 176 C 171 178 174 192 167 200 C 158 209 141 206 137 195 C 132 181 143 166 158 166 C 175 166 184 182 179 198" stroke="#7FB4F5" stroke-width="2.3" stroke-linecap="round" fill="none"/>' +
      ' <path d="M 117 177 C 124 203 186 203 193 177" stroke="#1E1E1E" stroke-width="3" stroke-linecap="round" fill="none"/>' +
      ' <path d="M 146 196 C 143 191 138 193 138 199 C 138 205 143 207 146 202 L 164 202 C 167 207 172 205 172 199 C 172 193 167 191 164 196 Z" fill="#FFFFFF" stroke="#1E1E1E" stroke-width="3" stroke-linejoin="round"/>' +
      ' <g class="face-expression">' +
      '  <g class="cheeks"><ellipse cx="127" cy="127" rx="6.5" ry="3.8" fill="#FF8A80"/><ellipse cx="183" cy="127" rx="6.5" ry="3.8" fill="#FF8A80"/></g>' +
      eyes +
      '  <path d="M 145 128 Q 150 134 155 129 Q 160 134 165 128" stroke="#1E1E1E" stroke-width="3" stroke-linecap="round" fill="none"/>' +
      ' </g>' +
      (extra || '') +
      '</g>';
  }

  function shadow() {
    return '<ellipse cx="170" cy="244" rx="80" ry="6" fill="#CBD5E1" opacity="0.45"/>';
  }

  function fuelBadge(dim) {
    return '<g transform="translate(14, 156)"' + (dim ? ' opacity="0.3" style="filter:grayscale(1)"' : '') + '>' +
      '<rect x="0" y="4" width="22" height="26" rx="5" fill="#FEF3C7" stroke="#F5A623" stroke-width="2.2"/>' +
      '<path d="M 11 10 C 11 10 16 16.5 16 19.5 A 5 5 0 1 1 6 19.5 C 6 16.5 11 10 11 10 Z" fill="#F5A623"/>' +
      '</g>';
  }

  // ───────── A. 발전 모드 SVG ─────────
  function genSVG() {
    var eyes =
      '<path d="M 132 115 L 140 119 L 132 123" stroke="#1E1E1E" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>' +
      '<path d="M 178 115 L 170 119 L 178 123" stroke="#1E1E1E" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>';
    var sweat =
      '<g class="sweat-drop"><path d="M 185 98 C 185 98 190 105 190 108.5 A 5 5 0 1 1 180 108.5 C 180 105 185 98 185 98 Z" fill="#60A5FA" stroke="#1E1E1E" stroke-width="2.2" stroke-linejoin="round"/></g>';
    return '' +
      '<svg viewBox="0 0 320 280" fill="none" xmlns="http://www.w3.org/2000/svg">' +
      '<g opacity="0.45">' +
      ' <path class="flow-guide-line" d="M 24 176 Q 56 174 86 184" stroke="#F5A623" stroke-width="2.2" stroke-linecap="round" stroke-dasharray="4 4"/>' +
      ' <path class="flow-guide-line" d="M 244 166 Q 251 163 257 168" stroke="#FBBF24" stroke-width="2.2" stroke-linecap="round" stroke-dasharray="4 4"/>' +
      '</g>' +
      '<g transform="translate(-12, 0)">' + shadow() + generator(1) + arms() + body(eyes, sweat) + '</g>' +
      '<g>' +
      ' <rect x="272" y="143" width="16" height="6" rx="2.5" fill="#475569" stroke="#1E1E1E" stroke-width="2.5"/>' +
      ' <rect x="261" y="149" width="38" height="64" rx="8" fill="#FFFFFF" stroke="#1E1E1E" stroke-width="3"/>' +
      ' <rect x="267" y="193" width="26" height="13" rx="3" fill="#F59E0B"/>' +
      ' <rect x="267" y="176" width="26" height="13" rx="3" fill="#FBBF24"/>' +
      ' <rect class="battery-bar-charging" x="267" y="159" width="26" height="13" rx="3" fill="#FDE047" stroke="#F59E0B" stroke-width="1.5"/>' +
      '</g>' +
      '<g>' + fuelBadge(false) +
      ' <g class="fuel-drop-1"><path d="M 44 166 C 44 166 50 174 50 177.5 A 6 6 0 1 1 38 177.5 C 38 174 44 166 44 166 Z" fill="#F5A623" stroke="#D97706" stroke-width="1.5"/></g>' +
      ' <g class="fuel-drop-2"><path d="M 44 168 C 44 168 49 174.5 49 177.5 A 5 5 0 1 1 39 177.5 C 39 174.5 44 168 44 168 Z" fill="#FBBF24" stroke="#D97706" stroke-width="1.5"/></g>' +
      ' <g transform="translate(22, -6)"><g class="energy-bolt-1"><path d="M 207 146 L 198 159 L 205 159 L 202 171 L 213 157 L 206 157 Z" fill="#FACC15" stroke="#B45309" stroke-width="1.8" stroke-linejoin="round"/></g></g>' +
      ' <g transform="translate(32, -6)"><g class="energy-bolt-2"><path d="M 196 144 L 190 153 L 195 153 L 193 161 L 200 152 L 195 152 Z" fill="#FEF08A" stroke="#D97706" stroke-width="1.5" stroke-linejoin="round"/></g></g>' +
      '</g>' +
      '</svg>';
  }

  // ───────── B. EV 모드 SVG ─────────
  function evSVG() {
    var eyes =
      '<path d="M 130 118 Q 136 123 142 118" stroke="#1E1E1E" stroke-width="3.2" stroke-linecap="round" fill="none"/>' +
      '<path d="M 168 118 Q 174 123 180 118" stroke="#1E1E1E" stroke-width="3.2" stroke-linecap="round" fill="none"/>';
    return '' +
      '<svg viewBox="0 0 320 280" fill="none" xmlns="http://www.w3.org/2000/svg">' +
      '<g opacity="0.8"><path d="M 24 176 Q 56 174 86 184" stroke="#CBD5E1" opacity="0.6" stroke-width="2.2" stroke-linecap="round" stroke-dasharray="4 4"/></g>' +
      '<g transform="translate(-12, 0)">' + shadow() + generator(0.35) + arms() + body(eyes, '') + '</g>' +
      '<g>' +
      ' <rect class="battery-halo" x="253" y="137" width="54" height="84" rx="14" fill="#34D399"/>' +
      ' <circle class="spark" cx="272" cy="136" r="2.6" fill="#10B981"/>' +
      ' <circle class="spark s2" cx="284" cy="134" r="2" fill="#34D399"/>' +
      ' <path class="spark s3" d="M 290 131 l -3 5 h 3 l -2 5 l 5 -6 h -3 l 2 -4 z" fill="#10B981"/>' +
      ' <rect x="272" y="143" width="16" height="6" rx="2.5" fill="#475569" stroke="#1E1E1E" stroke-width="2.5"/>' +
      ' <rect x="261" y="149" width="38" height="64" rx="8" fill="#FFFFFF" stroke="#1E1E1E" stroke-width="3"/>' +
      ' <rect x="267" y="193" width="26" height="13" rx="3" fill="#059669"/>' +
      ' <rect x="267" y="176" width="26" height="13" rx="3" fill="#10B981"/>' +
      ' <rect class="battery-bar-charging" x="267" y="159" width="26" height="13" rx="3" fill="#34D399" stroke="#059669" stroke-width="1.5"/>' +
      '</g>' +
      '<g>' + fuelBadge(true) +
      ' <g class="coin-sparkle">' +
      '  <circle cx="222" cy="112" r="9" fill="#FDE68A" stroke="#1E1E1E" stroke-width="2.4"/>' +
      '  <text x="222" y="116.5" text-anchor="middle" font-size="11" font-weight="800" fill="#B45309" font-family="Pretendard, sans-serif">₩</text>' +
      '  <path d="M 236 100 L 238 105 L 243 107 L 238 109 L 236 114 L 234 109 L 229 107 L 234 105 Z" fill="#FACC15"/>' +
      ' </g>' +
      '</g>' +
      '</svg>';
  }

  var styleInjected = false;
  function injectStyle() {
    if (styleInjected) return;
    var s = document.createElement('style');
    s.textContent = CSS;
    document.head.appendChild(s);
    styleInjected = true;
  }

  function setArm(g, sx, sy, hx, hy) {
    var lines = g.querySelectorAll('line');
    for (var i = 0; i < lines.length; i++) {
      lines[i].setAttribute('x1', sx); lines[i].setAttribute('y1', sy);
      lines[i].setAttribute('x2', hx); lines[i].setAttribute('y2', hy);
    }
    var h = g.querySelector('.hand');
    h.setAttribute('cx', hx); h.setAttribute('cy', hy);
  }

  var RATE = { normal: 1, calm: 0.5, off: 0 };
  var FROZEN_AT_MS = 900; // 'off'일 때 보여줄 정지 프레임 (번개·연료가 보이는 순간)

  function mount(el, opts) {
    opts = opts || {};
    injectStyle();
    el.classList.add('rw');
    el.innerHTML =
      '<div class="rw-layer rw-gen">' + genSVG() + '</div>' +
      '<div class="rw-layer rw-ev">' + evSVG() + '</div>';

    var gen = el.querySelector('.rw-gen'), ev = el.querySelector('.rw-ev');
    var g = {
      body: gen.querySelector('.rereureu-body'),
      crankArm: gen.querySelector('.crank-arm'), crankKnob: gen.querySelector('.crank-knob'),
      L: gen.querySelector('.left-arm'), R: gen.querySelector('.right-arm')
    };
    var e = { body: ev.querySelector('.rereureu-body'), L: ev.querySelector('.left-arm'), R: ev.querySelector('.right-arm') };

    var motion = 'normal', clock = 0, last = null, alive = true;

    function draw(t) {
      // A: 크랭크 한 바퀴 1.3초, 몸이 한 번 들썩
      var a = ((t % 1300) / 1300) * Math.PI * 2;
      var kx = 237 + Math.cos(a) * 10, ky = 178 + Math.sin(a) * 10;
      var bob = -2.2 * (0.5 - 0.5 * Math.cos(a));
      g.body.setAttribute('transform', 'translate(0 ' + bob.toFixed(2) + ')');
      g.crankArm.setAttribute('x2', kx.toFixed(2)); g.crankArm.setAttribute('y2', ky.toFixed(2));
      g.crankKnob.setAttribute('cx', kx.toFixed(2)); g.crankKnob.setAttribute('cy', ky.toFixed(2));
      setArm(g.R, 196, (160 + bob).toFixed(2), kx.toFixed(2), ky.toFixed(2));
      setArm(g.L, 114, (160 + bob).toFixed(2), 91, (188 + bob * 0.6 + Math.sin(a) * 1.5).toFixed(2));

      // B: 3.2초 숨쉬기
      var b = ((t % 3200) / 3200) * Math.PI * 2;
      var br = -1.4 * (0.5 - 0.5 * Math.cos(b));
      e.body.setAttribute('transform', 'translate(0 ' + br.toFixed(2) + ')');
      var sy = (160 + br).toFixed(2), hy = (190 + br * 0.5).toFixed(2);
      setArm(e.L, 114, sy, 96, hy);
      setArm(e.R, 196, sy, 214, hy);
    }

    function frame(now) {
      if (!alive) return;
      if (last != null) clock += (now - last) * RATE[motion];
      last = now;
      draw(motion === 'off' ? FROZEN_AT_MS : clock);
      requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);

    function applyMotion() {
      if (!el.getAnimations) return;
      el.getAnimations({ subtree: true }).forEach(function (an) {
        // 레이어 크로스페이드(transition)는 건드리지 않음
        if (typeof CSSTransition !== 'undefined' && an instanceof CSSTransition) return;
        if (motion === 'off') { an.pause(); an.currentTime = FROZEN_AT_MS; }
        else { an.playbackRate = RATE[motion]; an.play(); }
      });
    }

    var api = {
      setMode: function (m) {
        var isGen = m === 'gen';
        gen.classList.toggle('rw-hidden', !isGen);
        ev.classList.toggle('rw-hidden', isGen);
        api.mode = isGen ? 'gen' : 'ev';
      },
      setMotion: function (m) {
        motion = RATE.hasOwnProperty(m) ? m : 'normal';
        api.motion = motion;
        applyMotion();
      },
      destroy: function () { alive = false; el.innerHTML = ''; }
    };
    api.setMode(opts.mode || 'ev');
    api.setMotion(opts.motion || 'normal');
    // CSS 애니메이션이 확실히 생성된 다음 프레임에 (그 사이 바뀐 값까지 포함해) 한 번 더 적용
    requestAnimationFrame(function () { api.setMotion(api.motion); });
    return api;
  }

  window.RrrWidget = { mount: mount };
})();
