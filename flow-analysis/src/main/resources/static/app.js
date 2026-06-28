/* =============================================================================
 * app.js — 看板编排层（控件 → API → 图表渲染 联动）
 * -----------------------------------------------------------------------------
 * 职责：
 *  1) 启动时拉 /api/regions 填充区域下拉（不硬编码）。
 *  2) 收集控件状态为 ctx，调用 window.renderTrend / renderOd / renderPortrait。
 *  3) “查询”按钮全量刷新；控件变更联动刷新对应模块。
 *  4) “初始化数据”按钮 POST /api/admin/init-data 后重载区域并刷新。
 *  5) 概览卡片由趋势数据聚合（属外壳职责，非图表桩）。
 *
 * 容器 id 约定（供 T15/16/17 对接）：
 *  #chart-trend | #chart-od + #od-matrix | #chart-portrait-{gender,age,resident} | #overview-cards
 * renderXxx 签名详见 charts/*.js 头部。
 * ========================================================================== */
(function () {
  'use strict';

  var PORTRAIT_CONTAINER = {
    gender: 'chart-portrait-gender',
    age_group: 'chart-portrait-age',
    is_resident: 'chart-portrait-resident'
  };
  var PORTRAIT_DIMENSIONS = ['gender', 'age_group', 'is_resident'];

  var el = {};
  var regions = [];

  function $(id) { return document.getElementById(id); }

  function cacheEls() {
    ['region', 'granularity', 'start', 'end', 'direction', 'dimension', 'btn-query', 'btn-init']
      .forEach(function (id) { el[id] = $(id); });
  }

  function normalizeIso(v) {
    if (!v) { return v; }
    var parts = String(v).split('T');
    var seg = (parts[1] || '00:00:00').split(':');
    while (seg.length < 3) { seg.push('00'); }
    return parts[0] + 'T' + seg.slice(0, 3).join(':');
  }

  function selectedRegionName() {
    var id = el.region && el.region.value;
    var hit = regions.filter(function (r) { return r.regionId === id; })[0];
    return hit ? hit.regionName : '';
  }

  function collectCtx() {
    return {
      regionId: el.region ? el.region.value : '',
      regionName: selectedRegionName(),
      granularity: el.granularity ? el.granularity.value : 'hour',
      start: normalizeIso(el.start ? el.start.value : ''),
      end: normalizeIso(el.end ? el.end.value : ''),
      direction: el.direction ? el.direction.value : 'in',
      dimension: el.dimension ? el.dimension.value : 'gender',
      regions: regions,
      echarts: window.echarts,
      matrixId: 'od-matrix'
    };
  }

  function fmtInt(n) {
    if (typeof n !== 'number' || isNaN(n)) { return '—'; }
    var s = (n < 0 ? '-' : '') + Math.abs(Math.round(n)).toLocaleString('en-US');
    return s;
  }

  function updateOverview(trend) {
    var population = '—', inflowSum = 0, outflowSum = 0, hasData = Array.isArray(trend) && trend.length > 0;
    if (hasData) {
      trend.forEach(function (p) {
        inflowSum += (p.inflow || 0);
        outflowSum += (p.outflow || 0);
      });
      population = trend[trend.length - 1].population;
    }
    var set = function (key, val) {
      var node = document.querySelector('#overview-cards [data-metric="' + key + '"]');
      if (node) { node.textContent = (typeof val === 'number') ? fmtInt(val) : val; }
    };
    set('population', hasData ? population : '—');
    set('inflow', hasData ? inflowSum : '—');
    set('outflow', hasData ? outflowSum : '—');
    set('net', hasData ? (inflowSum - outflowSum) : '—');
  }

  function refreshTrend() {
    var ctx = collectCtx();
    return window.api.getTrend(ctx)
      .then(function (data) {
        if (typeof window.renderTrend === 'function') { window.renderTrend('chart-trend', data, ctx); }
        updateOverview(data);
      })
      .catch(function () { /* api.js 已 toast，吞掉以避免未处理 rejection */ });
  }

  function refreshOd() {
    var ctx = collectCtx();
    return window.api.getOd(ctx)
      .then(function (data) {
        if (typeof window.renderOd === 'function') { window.renderOd('chart-od', data, ctx); }
      })
      .catch(function () {});
  }

  function refreshPortrait() {
    var ctx = collectCtx();
    var jobs = PORTRAIT_DIMENSIONS.map(function (dim) {
      var p = collectCtx();
      p.dimension = dim;
      return window.api.getPortrait(p)
        .then(function (data) {
          if (typeof window.renderPortrait === 'function') {
            window.renderPortrait(PORTRAIT_CONTAINER[dim], data, Object.assign({}, ctx, { dimension: dim, activeDimension: ctx.dimension }));
          }
        })
        .catch(function () {});
    });
    return Promise.all(jobs);
  }

  function queryAll() {
    return Promise.all([refreshTrend(), refreshOd(), refreshPortrait()]);
  }

  function loadRegions() {
    return window.api.getRegions()
      .then(function (data) {
        regions = Array.isArray(data) ? data : [];
        if (el.region) {
          el.region.innerHTML = regions.map(function (r) {
            return '<option value="' + r.regionId + '">' + r.regionName + '（' + r.regionId + '）</option>';
          }).join('');
        }
      });
  }

  function bindEvents() {
    if (el['btn-query']) { el['btn-query'].addEventListener('click', function () { queryAll(); }); }

    if (el['btn-init']) {
      el['btn-init'].addEventListener('click', function () {
        var ok = window.confirm('初始化数据将重建底层明细与派生表（约 2~3 分钟），期间请勿重复点击。确认继续？');
        if (!ok) { return; }
        window.api.initData()
          .then(function (res) {
            var rows = res && res.dwdRows ? ('，明细 ' + fmtInt(res.dwdRows) + ' 行') : '';
            window.ui.toast('数据初始化完成' + rows, 'success');
            return loadRegions();
          })
          .then(function () { return queryAll(); })
          .catch(function () {});
      });
    }

    [el.region, el.granularity, el.start, el.end].forEach(function (node) {
      if (node) { node.addEventListener('change', function () { queryAll(); }); }
    });
    [el.direction, el.dimension].forEach(function (node) {
      if (node) { node.addEventListener('change', function () { refreshPortrait(); }); }
    });
  }

  function init() {
    cacheEls();
    bindEvents();
    if (!window.echarts) {
      window.ui.toast('ECharts 本地库未加载，图表可能无法显示', 'error');
    }
    loadRegions()
      .then(function () { return queryAll(); })
      .catch(function () {});
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
