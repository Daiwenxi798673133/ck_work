/* =============================================================================
 * charts/trend.js — 流动趋势图模块【T15 实现】
 * -----------------------------------------------------------------------------
 * 容器：#chart-trend
 * 数据源：GET /api/flow/trend（ApiResp.data，已由 api.js 解包，按 windowStart 升序）
 *
 * 函数签名（app.js 依赖此约定，保持不变）：
 *   window.renderTrend(containerId, data, ctx)
 *     @param {string} containerId  图表容器 DOM id，固定 'chart-trend'
 *     @param {Array<{
 *               windowStart:string,  // ISO 'yyyy-MM-ddTHH:mm:ss'
 *               population:number,    // 在网人数 = retained + inflow
 *               inflow:number,        // 流入人次
 *               outflow:number,       // 流出人次
 *               retained:number       // 留存人次
 *            }>} data
 *     @param {object} ctx  控件上下文（见 app.js collectCtx）：
 *            { regionId, regionName, granularity, start, end,
 *              direction, dimension, regions, echarts, matrixId }
 * ========================================================================== */
(function () {
  'use strict';

  function cssVar(name, fallback) {
    try {
      var v = getComputedStyle(document.documentElement).getPropertyValue(name);
      return (v && v.trim()) || fallback;
    } catch (e) {
      return fallback;
    }
  }

  function hexToRgba(hex, alpha) {
    var h = String(hex || '').trim().replace('#', '');
    if (h.length === 3) {
      h = h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
    }
    var n = parseInt(h, 16);
    if (h.length !== 6 || isNaN(n)) { return 'rgba(8,145,178,' + alpha + ')'; }
    var r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
    return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')';
  }

  function toNum(v) { var n = Number(v); return isFinite(n) ? n : 0; }

  function fmtNum(v) {
    var n = Number(v);
    if (!isFinite(n)) { return '—'; }
    return n.toLocaleString('en-US');
  }

  function fmtYAxis(v) {
    var n = Number(v);
    if (!isFinite(n)) { return ''; }
    if (Math.abs(n) >= 10000) {
      return (Math.round((n / 10000) * 10) / 10) + '万';
    }
    return String(n);
  }

  function fmtAxisLabel(iso, granularity) {
    var s = String(iso || '');
    var md = s.slice(5, 10);
    var hm = s.slice(11, 16);
    if (granularity === 'day') { return md; }
    return md + '\n' + hm;
  }

  function fmtFull(iso, granularity) {
    var s = String(iso || '').replace('T', ' ');
    return granularity === 'day' ? s.slice(0, 10) : s.slice(0, 16);
  }

  function getChart(echarts, el) {
    var inst = echarts.getInstanceByDom(el);
    if (inst) { return inst; }
    el.innerHTML = '';
    return echarts.init(el);
  }

  var resizeBound = false;
  function bindResizeOnce() {
    if (resizeBound) { return; }
    resizeBound = true;
    window.addEventListener('resize', function () {
      var node = document.getElementById('chart-trend');
      if (!node || !window.echarts) { return; }
      var inst = window.echarts.getInstanceByDom(node);
      if (inst) { inst.resize(); }
    });
  }

  function showPlaceholder(el, text) {
    el.innerHTML =
      '<div class="chart-placeholder">' +
        '<span class="ph-tag">流动趋势</span>' +
        '<span class="ph-meta">' + text + '</span>' +
      '</div>';
  }

  function areaFill(echarts, color) {
    var c0 = hexToRgba(color, 0.22), c1 = hexToRgba(color, 0.02);
    if (echarts.graphic && echarts.graphic.LinearGradient) {
      return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: c0 },
        { offset: 1, color: c1 }
      ]);
    }
    return c0;
  }

  function lineSeries(name, color, arr, opts) {
    opts = opts || {};
    var s = {
      name: name,
      type: 'line',
      smooth: true,
      smoothMonotone: 'x',
      showSymbol: false,
      symbol: 'circle',
      symbolSize: 6,
      sampling: 'lttb',
      lineStyle: { width: opts.width || 2, color: color },
      itemStyle: { color: color },
      emphasis: { focus: 'series' },
      z: opts.z || 2,
      data: arr
    };
    if (opts.area) { s.areaStyle = { opacity: 1, color: opts.area }; }
    return s;
  }

  window.renderTrend = function renderTrend(containerId, data, ctx) {
    var el = document.getElementById(containerId);
    if (!el) { return; }

    var echarts = (ctx && ctx.echarts) || window.echarts;
    var granularity = (ctx && ctx.granularity) || 'hour';

    if (!echarts) {
      showPlaceholder(el, 'ECharts 本地库未加载');
      return;
    }

    if (!Array.isArray(data) || data.length === 0) {
      var stale = echarts.getInstanceByDom(el);
      if (stale) { stale.dispose(); }
      showPlaceholder(el, '无数据（当前区域 / 时间区间无流动记录）');
      return;
    }

    var FONT = cssVar('--font', '"PingFang SC","Helvetica Neue",sans-serif');
    var C = {
      population: cssVar('--primary-500', '#06b6d4'),
      inflow: cssVar('--inflow', '#16a34a'),
      outflow: cssVar('--outflow', '#ea580c'),
      retained: cssVar('--retained', '#2563eb')
    };
    var textMuted = cssVar('--text-muted', '#56657d');
    var textColor = cssVar('--text', '#0f1b2d');
    var border = cssVar('--border', '#e4e9f0');
    var borderStrong = cssVar('--border-strong', '#d3dbe7');
    var surface = cssVar('--surface', '#ffffff');

    var cats = data.map(function (p) { return String(p.windowStart || ''); });
    var sPop = data.map(function (p) { return toNum(p.population); });
    var sIn = data.map(function (p) { return toNum(p.inflow); });
    var sOut = data.map(function (p) { return toNum(p.outflow); });
    var sRet = data.map(function (p) { return toNum(p.retained); });

    var manyPoints = cats.length > 24;
    var dataZoom = manyPoints
      ? [
          { type: 'inside', throttle: 50 },
          {
            type: 'slider', height: 18, bottom: 12,
            borderColor: border, fillerColor: hexToRgba(C.population, 0.12),
            handleSize: '120%', textStyle: { color: textMuted, fontSize: 11 }
          }
        ]
      : [{ type: 'inside', throttle: 50 }];

    var option = {
      color: [C.population, C.inflow, C.outflow, C.retained],
      textStyle: { fontFamily: FONT, color: textMuted },
      animationDuration: 600,
      animationEasing: 'cubicOut',
      grid: {
        left: 10, right: 20, top: 54,
        bottom: manyPoints ? 64 : 28,
        containLabel: true
      },
      legend: {
        top: 14,
        icon: 'roundRect',
        itemWidth: 16, itemHeight: 9, itemGap: 20,
        textStyle: { color: textMuted, fontSize: 13, fontWeight: 600, fontFamily: FONT },
        data: ['在网人数', '流入', '流出', '留存']
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: surface,
        borderColor: border,
        borderWidth: 1,
        padding: [10, 12],
        textStyle: { color: textColor, fontFamily: FONT, fontSize: 13 },
        extraCssText: 'box-shadow:0 16px 40px rgba(15,27,45,.12);border-radius:12px;',
        axisPointer: {
          type: 'line',
          lineStyle: { color: borderStrong, width: 1, type: 'dashed' }
        },
        formatter: function (params) {
          if (!params || !params.length) { return ''; }
          var title = fmtFull(params[0].axisValue, granularity);
          var rows = params.map(function (p) {
            return '<div style="display:flex;align-items:center;justify-content:space-between;gap:24px;line-height:1.7;">' +
              '<span>' + p.marker + p.seriesName + '</span>' +
              '<b style="font-variant-numeric:tabular-nums;">' + fmtNum(p.value) + '</b>' +
              '</div>';
          }).join('');
          return '<div style="font-weight:700;margin-bottom:6px;">' + title + '</div>' + rows;
        }
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: cats,
        axisLine: { lineStyle: { color: borderStrong } },
        axisTick: { show: false },
        axisLabel: {
          color: textMuted,
          fontSize: 11,
          hideOverlap: true,
          margin: 12,
          formatter: function (val) { return fmtAxisLabel(val, granularity); }
        }
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: textMuted, fontSize: 11, formatter: fmtYAxis },
        splitLine: { lineStyle: { color: border, type: 'dashed' } }
      },
      dataZoom: dataZoom,
      series: [
        lineSeries('在网人数', C.population, sPop, { width: 3, z: 1, area: areaFill(echarts, C.population) }),
        lineSeries('流入', C.inflow, sIn, { width: 2, z: 3 }),
        lineSeries('流出', C.outflow, sOut, { width: 2, z: 3 }),
        lineSeries('留存', C.retained, sRet, { width: 2, z: 3 })
      ]
    };

    var chart = getChart(echarts, el);
    chart.setOption(option, true);
    bindResizeOnce();
  };
})();
