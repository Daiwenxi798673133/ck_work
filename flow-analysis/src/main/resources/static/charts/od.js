/* =============================================================================
 * charts/od.js — OD 流向模块【T16 实现】
 * -----------------------------------------------------------------------------
 * 容器：#chart-od（ECharts 桑基图 sankey） + #od-matrix（HTML N×N 矩阵表，ctx.matrixId）
 * 数据源：GET /api/flow/od（ApiResp.data，已由 api.js 解包）
 *
 * 函数签名（app.js 依赖此约定）：
 *   window.renderOd(containerId, data, ctx)
 *     @param {string} containerId  ECharts 容器 id，固定 'chart-od'
 *     @param {Array<{
 *               fromRegionId:string, fromRegionName:string,
 *               toRegionId:string,   toRegionName:string,
 *               flow:number          // 时段内 from→to 总流量；from===to 即对角线=留存
 *            }>} data                // 3 region → 至多 9 项（含对角线）
 *     @param {object} ctx  控件上下文；ctx.matrixId='od-matrix'、ctx.echarts=window.echarts。
 *
 * 实现要点：
 *  ① 桑基图：仅画 from!==to 的「跨区流动」（过滤对角线留存）。
 *     ECharts sankey 不支持有环图——3 个区域两两互流是有环的，故采用「左右双列」二部图：
 *     起点节点加前缀 'src\x01<name>'（depth 0），终点节点加前缀 'dst\x01<name>'（depth 1），
 *     所有 link 一律自左向右，结构上不可能成环；label/tooltip 用 formatter 去前缀显示纯区域名。
 *  ② 矩阵表：在 #od-matrix 注入 N×N 的 HTML <table>，行=from 列=to，含对角线（高亮=留存），
 *     数值千分位；非对角线按流量做热力底色，呼应「OD 流向热力」面板。
 *  ③ 颜色取自 style.css 的 :root 设计令牌（getComputedStyle 读取，canvas 无法直接用 CSS 变量）；
 *     矩阵表 CSS 由本模块一次性注入 <style>，内部直接用 var(--…) 复用同一套令牌。
 *  ④ 空数据/无 ECharts/无跨区 均显示占位、不抛错；复用 ECharts 实例并自适应 resize。
 * ========================================================================== */
(function () {
  'use strict';

  var RESIZE_BOUND = false;
  var STYLE_ID = 'od-matrix-style-v1';
  var SRC = 'src\u0001';
  var DST = 'dst\u0001';

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  function fmtInt(n) {
    var v = Number(n);
    if (!isFinite(v)) { return '0'; }
    return Math.round(v).toLocaleString('en-US');
  }

  // 读取 :root 设计令牌；canvas 渲染需字面值，故用 getComputedStyle 取真实色值（带兜底）。
  function cssVar(name, fallback) {
    try {
      var v = getComputedStyle(document.documentElement).getPropertyValue(name);
      v = v && v.trim();
      return v || fallback;
    } catch (e) { return fallback; }
  }

  function hexToRgb(hex) {
    var h = String(hex || '').trim().replace('#', '');
    if (h.length === 3) { h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2]; }
    var int = parseInt(h, 16);
    if (h.length !== 6 || isNaN(int)) { return { r: 8, g: 145, b: 178 }; }
    return { r: (int >> 16) & 255, g: (int >> 8) & 255, b: int & 255 };
  }

  function palette() {
    return [
      cssVar('--primary-600', '#0891b2'),
      cssVar('--retained', '#2563eb'),
      cssVar('--outflow', '#ea580c'),
      cssVar('--inflow', '#16a34a'),
      cssVar('--primary-500', '#06b6d4'),
      '#9333ea'
    ];
  }

  function regionOrder(data) {
    var seen = {}, list = [];
    function add(id, name) {
      if (id != null && !seen[id]) { seen[id] = true; list.push({ id: id, name: name }); }
    }
    data.forEach(function (d) { add(d.fromRegionId, d.fromRegionName); });
    data.forEach(function (d) { add(d.toRegionId, d.toRegionName); });
    list.sort(function (a, b) {
      var x = String(a.id), y = String(b.id);
      return x < y ? -1 : (x > y ? 1 : 0);
    });
    return list;
  }

  function colorMap(order) {
    var pal = palette(), map = {};
    order.forEach(function (r, i) { map[r.name] = pal[i % pal.length]; });
    return map;
  }

  function placeholder(el, tag, meta) {
    el.innerHTML =
      '<div class="chart-placeholder">' +
        '<span class="ph-tag">' + esc(tag) + '</span>' +
        '<span class="ph-meta">' + esc(meta) + '</span>' +
      '</div>';
  }

  function disposeChart(echarts, el) {
    var c = echarts && el && echarts.getInstanceByDom(el);
    if (c) { c.dispose(); }
  }

  function stripPrefix(name) {
    return String(name).replace(/^(?:src|dst)\u0001/, '');
  }

  function renderSankey(containerId, data, ctx) {
    var el = document.getElementById(containerId);
    if (!el) { return; }
    var echarts = (ctx && ctx.echarts) || window.echarts;

    if (!echarts) {
      placeholder(el, 'OD 桑基图', 'ECharts 未加载，无法渲染图表');
      return;
    }
    if (!data.length) {
      disposeChart(echarts, el);
      placeholder(el, 'OD 桑基图', '当前区间暂无 OD 流向数据');
      return;
    }

    var cross = data.filter(function (d) {
      return d.fromRegionId !== d.toRegionId && Number(d.flow) > 0;
    });
    if (!cross.length) {
      disposeChart(echarts, el);
      placeholder(el, 'OD 桑基图', '区间内无跨区流动（仅有同区留存）');
      return;
    }

    var cmap = colorMap(regionOrder(data));
    var textColor = cssVar('--text', '#0f1b2d');
    var mutedColor = cssVar('--text-muted', '#56657d');

    // 二部图：起点节点（depth0, label 居右）/ 终点节点（depth1, label 居左），杜绝环。
    var srcSeen = {}, dstSeen = {}, nodes = [], links = [];
    cross.forEach(function (d) {
      var s = SRC + d.fromRegionName;
      var t = DST + d.toRegionName;
      if (!srcSeen[s]) {
        srcSeen[s] = true;
        nodes.push({ name: s, depth: 0, itemStyle: { color: cmap[d.fromRegionName] }, label: { position: 'right' } });
      }
      if (!dstSeen[t]) {
        dstSeen[t] = true;
        nodes.push({ name: t, depth: 1, itemStyle: { color: cmap[d.toRegionName] }, label: { position: 'left' } });
      }
      links.push({ source: s, target: t, value: Number(d.flow) });
    });

    var option = {
      backgroundColor: 'transparent',
      title: {
        text: '区域间跨区流向',
        subtext: '左＝起点 · 右＝终点 · 已隐去对角线留存',
        left: 'center', top: 6,
        textStyle: { fontSize: 13, fontWeight: 700, color: textColor },
        subtextStyle: { fontSize: 11, color: mutedColor }
      },
      tooltip: {
        trigger: 'item', confine: true,
        backgroundColor: 'rgba(15,27,45,.92)', borderWidth: 0,
        textStyle: { color: '#fff', fontSize: 12 },
        formatter: function (p) {
          if (p.dataType === 'edge') {
            return stripPrefix(p.data.source) + ' &#8594; ' + stripPrefix(p.data.target) +
              '<br/>跨区流量：<b>' + fmtInt(p.data.value) + '</b>';
          }
          return '<b>' + stripPrefix(p.name) + '</b>';
        }
      },
      series: [{
        type: 'sankey',
        left: '6%', right: '6%', top: 58, bottom: 18,
        nodeWidth: 18, nodeGap: 16, nodeAlign: 'justify', draggable: false,
        emphasis: { focus: 'adjacency' },
        data: nodes,
        links: links,
        label: {
          color: textColor, fontSize: 12, fontWeight: 600,
          formatter: function (p) { return stripPrefix(p.name); }
        },
        lineStyle: { color: 'gradient', opacity: 0.42, curveness: 0.5 },
        itemStyle: { borderWidth: 0, borderColor: 'transparent' }
      }]
    };

    var chart = echarts.getInstanceByDom(el);
    if (!chart) { el.innerHTML = ''; chart = echarts.init(el); }
    chart.setOption(option, true);

    if (!RESIZE_BOUND) {
      RESIZE_BOUND = true;
      window.addEventListener('resize', function () {
        var c = echarts.getInstanceByDom(document.getElementById(containerId));
        if (c) { c.resize(); }
      });
    }
  }

  function ensureStyle() {
    if (document.getElementById(STYLE_ID)) { return; }
    var css =
      '.odm-wrap{width:100%;overflow-x:auto;}' +
      '.odm{width:100%;border-collapse:separate;border-spacing:0;font-family:var(--font-num);' +
        'font-size:var(--fs-sm);color:var(--text);}' +
      '.odm caption{caption-side:top;text-align:left;color:var(--text-muted);font-size:var(--fs-xs);' +
        'font-weight:700;padding-bottom:var(--space-2);}' +
      '.odm th,.odm td{padding:9px 12px;border-bottom:1px solid var(--border);white-space:nowrap;}' +
      '.odm thead th{background:var(--surface-2);color:var(--text-muted);font-weight:700;' +
        'font-size:var(--fs-xs);text-align:right;letter-spacing:.02em;border-bottom:1px solid var(--border-strong);}' +
      '.odm thead th:first-child{text-align:left;}' +
      '.odm__corner{color:var(--text-faint);font-weight:700;}' +
      '.odm__rowhead{text-align:left;color:var(--text);font-weight:700;background:var(--surface-2);' +
        'border-right:1px solid var(--border);}' +
      '.odm td{text-align:right;font-variant-numeric:tabular-nums;}' +
      '.odm__diag{background:var(--retained-050);color:var(--retained);font-weight:800;}' +
      '.odm tbody tr:last-child th,.odm tbody tr:last-child td{border-bottom:none;}' +
      '.odm tbody tr:hover .odm__rowhead{color:var(--primary);}' +
      '.odm-note{display:flex;align-items:center;gap:8px;margin-top:var(--space-3);' +
        'color:var(--text-faint);font-size:var(--fs-xs);}' +
      '.odm-note__dot{width:12px;height:12px;border-radius:4px;background:var(--retained-050);' +
        'border:1px solid var(--retained);flex:0 0 auto;}';
    var style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = css;
    document.head.appendChild(style);
  }

  function renderMatrix(matrixId, data, ctx) {
    var mt = document.getElementById(matrixId);
    if (!mt) { return; }
    if (!data.length) {
      placeholder(mt, 'OD 矩阵表', '当前区间暂无 OD 流向数据');
      return;
    }
    ensureStyle();

    var order = regionOrder(data);
    var fmap = {};
    data.forEach(function (d) { fmap[d.fromRegionId + '\u0001' + d.toRegionId] = Number(d.flow) || 0; });

    var rgb = hexToRgb(cssVar('--primary-600', '#0891b2'));
    var maxOff = 0;
    order.forEach(function (rf) {
      order.forEach(function (rt) {
        if (rf.id !== rt.id) {
          var v = fmap[rf.id + '\u0001' + rt.id] || 0;
          if (v > maxOff) { maxOff = v; }
        }
      });
    });

    var html = '<div class="odm-wrap"><table class="odm">' +
      '<caption>行＝起点（from） · 列＝终点（to） · 单元格＝时段总流量</caption>' +
      '<thead><tr><th class="odm__corner" scope="col">起点＼终点</th>';
    order.forEach(function (rt) { html += '<th scope="col">' + esc(rt.name) + '</th>'; });
    html += '</tr></thead><tbody>';

    order.forEach(function (rf) {
      html += '<tr><th class="odm__rowhead" scope="row">' + esc(rf.name) + '</th>';
      order.forEach(function (rt) {
        var v = fmap[rf.id + '\u0001' + rt.id] || 0;
        if (rf.id === rt.id) {
          html += '<td class="odm__diag" title="留存 retained">' + fmtInt(v) + '</td>';
        } else {
          var a = (maxOff > 0 && v > 0) ? (0.06 + 0.42 * (v / maxOff)) : 0;
          var st = a > 0
            ? ' style="background:rgba(' + rgb.r + ',' + rgb.g + ',' + rgb.b + ',' + a.toFixed(3) + ')"'
            : '';
          html += '<td' + st + '>' + fmtInt(v) + '</td>';
        }
      });
      html += '</tr>';
    });

    html += '</tbody></table>' +
      '<div class="odm-note"><span class="odm-note__dot"></span>' +
        '对角线（同区）＝留存 retained；非对角线＝跨区流量，色深表示流量更大</div>' +
      '</div>';
    mt.innerHTML = html;
  }

  window.renderOd = function renderOd(containerId, data, ctx) {
    var arr = Array.isArray(data) ? data : [];
    renderSankey(containerId, arr, ctx);
    renderMatrix((ctx && ctx.matrixId) || 'od-matrix', arr, ctx);
  };
})();
