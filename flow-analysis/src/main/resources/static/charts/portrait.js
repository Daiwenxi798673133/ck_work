/* =============================================================================
 * charts/portrait.js — 人群画像分布模块（实现 window.renderPortrait）
 * -----------------------------------------------------------------------------
 * 公开契约（app.js 依赖此约定）：
 *   window.renderPortrait(containerId, data, ctx)
 *     containerId ∈ {chart-portrait-gender, chart-portrait-age, chart-portrait-resident}
 *     data = { direction, dimension, total, buckets:[{bucket, count, ratio∈[0,1]}] }
 *     ctx  = { direction, dimension, activeDimension, echarts, regionId, granularity, start, end, ... }
 *
 *   gender→饼图(男/女)；age_group→分组柱图(6档,流入/流出两系列)；is_resident→环图("0"非常住/"1"常住)。
 *   占比一律用后端 ratio；颜色取自 style.css 设计令牌（语义色 流入/流出 复用 --inflow/--outflow，
 *   分类色 男/女、常住/非常住 为 CSS 令牌未覆盖的图表局部扩展）。
 * ========================================================================== */
(function () {
  'use strict';

  var AGE_ORDER = ['<18', '18-25', '26-35', '36-45', '46-60', '60+'];
  var RESIDENT_LABEL = { '1': '常住', '0': '非常住' };

  var _palette = null;
  function token(name, fallback) {
    try {
      var v = getComputedStyle(document.documentElement).getPropertyValue(name);
      v = v && v.trim();
      return v || fallback;
    } catch (e) { return fallback; }
  }
  function palette() {
    if (_palette) { return _palette; }
    _palette = {
      inflow: token('--inflow', '#16a34a'),
      outflow: token('--outflow', '#ea580c'),
      male: token('--retained', '#2563eb'),
      female: '#e0529c',
      residentYes: token('--primary', '#0e7490'),
      residentNo: token('--text-faint', '#93a1b5'),
      primary: token('--primary', '#0e7490'),
      text: token('--text', '#0f1b2d'),
      muted: token('--text-muted', '#56657d'),
      faint: token('--text-faint', '#93a1b5'),
      border: token('--border-strong', '#d3dbe7')
    };
    return _palette;
  }

  function dirLabel(d) { return d === 'out' ? '流出' : '流入'; }

  function pct(ratio) {
    var v = (typeof ratio === 'number' && isFinite(ratio)) ? ratio * 100 : 0;
    return (Math.round(v * 10) / 10).toFixed(1) + '%';
  }

  function fmtInt(n) {
    if (typeof n !== 'number' || !isFinite(n)) { return '0'; }
    return Math.round(n).toLocaleString('en-US');
  }

  function isEmpty(data) {
    return !data || !Array.isArray(data.buckets) || data.buckets.length === 0 || !(data.total > 0);
  }

  function bucketMap(data) {
    var m = {};
    if (data && Array.isArray(data.buckets)) {
      data.buckets.forEach(function (b) {
        m[b.bucket] = {
          count: typeof b.count === 'number' ? b.count : 0,
          ratio: typeof b.ratio === 'number' ? b.ratio : 0
        };
      });
    }
    return m;
  }

  function dimFromContainer(id) {
    if (id === 'chart-portrait-age') { return 'age_group'; }
    if (id === 'chart-portrait-resident') { return 'is_resident'; }
    return 'gender';
  }

  function getInst(el, lib) {
    var inst = lib.getInstanceByDom ? lib.getInstanceByDom(el) : null;
    if (!inst) {
      el.innerHTML = '';
      if (el.clientHeight < 10) { el.style.minHeight = '280px'; }
      inst = lib.init(el);
      if (!el.__pResize) {
        el.__pResize = true;
        window.addEventListener('resize', function () {
          var i = lib.getInstanceByDom ? lib.getInstanceByDom(el) : null;
          if (i) { i.resize(); }
        });
      }
    }
    return inst;
  }

  function titleStyle(text, sub, isActive) {
    var c = palette();
    return {
      text: text,
      subtext: (isActive ? '【当前选中】 ' : '') + (sub || ''),
      left: 'center',
      top: 6,
      textStyle: { fontSize: 13, fontWeight: 700, color: isActive ? c.primary : c.text },
      subtextStyle: { fontSize: 11, color: c.muted }
    };
  }

  function setEmpty(inst, sub, isActive) {
    var c = palette();
    inst.setOption({
      title: [
        titleStyle('暂无数据', sub || '', isActive),
        {
          text: '该方向 / 区间暂无画像数据',
          left: 'center', top: 'middle',
          textStyle: { color: c.faint, fontSize: 13, fontWeight: 600 }
        }
      ],
      xAxis: { show: false },
      yAxis: { show: false },
      series: []
    }, true);
  }

  function drawPie(inst, o) {
    var c = palette();
    var isGender = o.kind === 'gender';
    var dimName = isGender ? '性别构成' : '常住构成';

    if (isEmpty(o.data)) {
      setEmpty(inst, dirLabel(o.curDir) + ' · ' + (isGender ? '性别' : '常住'), o.isActive);
      return;
    }

    var labelOf = function (bucket) {
      return isGender ? bucket : (RESIDENT_LABEL[bucket] || bucket);
    };
    var colorOf = function (bucket) {
      if (isGender) { return bucket === '女' ? c.female : c.male; }
      return bucket === '1' ? c.residentYes : c.residentNo;
    };

    var otherMap = bucketMap(o.other);
    var hasOther = o.other && !isEmpty(o.other);

    var seriesData = o.data.buckets.map(function (b) {
      return {
        name: labelOf(b.bucket),
        value: typeof b.count === 'number' ? b.count : 0,
        ratio: typeof b.ratio === 'number' ? b.ratio : 0,
        _bucket: b.bucket,
        itemStyle: { color: colorOf(b.bucket) }
      };
    });

    var sub = dirLabel(o.curDir) + ' · 共 ' + fmtInt(o.data.total) + ' 次' +
      (hasOther ? '（' + dirLabel(o.otherDir) + ' ' + fmtInt(o.other.total) + '）' : '');

    inst.setOption({
      title: titleStyle(dimName, sub, o.isActive),
      tooltip: {
        trigger: 'item',
        confine: true,
        formatter: function (p) {
          var lines = ['<b>' + p.name + '</b>',
            dirLabel(o.curDir) + '：' + fmtInt(p.data.value) + ' 次 · ' + pct(p.data.ratio)];
          var ob = otherMap[p.data._bucket];
          if (ob) {
            lines.push(dirLabel(o.otherDir) + '：' + fmtInt(ob.count) + ' 次 · ' + pct(ob.ratio));
          }
          return lines.join('<br/>');
        }
      },
      legend: {
        bottom: 4, itemWidth: 11, itemHeight: 11,
        textStyle: { color: c.muted, fontSize: 12 }
      },
      series: [{
        type: 'pie',
        radius: isGender ? ['0%', '62%'] : ['42%', '64%'],
        center: ['50%', '54%'],
        avoidLabelOverlap: true,
        minAngle: 4,
        label: {
          color: c.text, fontSize: 12, lineHeight: 16,
          formatter: function (p) { return p.name + '\n' + pct(p.data.ratio); }
        },
        labelLine: { length: 12, length2: 10 },
        emphasis: { itemStyle: { shadowBlur: 12, shadowColor: 'rgba(15,27,45,0.18)' } },
        data: seriesData
      }]
    }, true);
  }

  function drawAge(inst, inData, outData, curDir, otherDir, isActive) {
    var c = palette();
    var hasIn = inData && !isEmpty(inData);
    var hasOut = outData && !isEmpty(outData);

    if (!hasIn && !hasOut) {
      setEmpty(inst, '年龄段 · ' + dirLabel(curDir), isActive);
      return;
    }

    var inMap = bucketMap(inData);
    var outMap = bucketMap(outData);
    var mk = function (map) {
      return AGE_ORDER.map(function (a) {
        var hit = map[a];
        return { value: hit ? hit.count : 0, ratio: hit ? hit.ratio : 0 };
      });
    };

    var series = [];
    if (hasIn) {
      series.push({
        name: '流入', type: 'bar', barMaxWidth: 22,
        itemStyle: { color: c.inflow, borderRadius: [4, 4, 0, 0] },
        emphasis: { itemStyle: { color: c.inflow } },
        data: mk(inMap)
      });
    }
    if (hasOut) {
      series.push({
        name: '流出', type: 'bar', barMaxWidth: 22,
        itemStyle: { color: c.outflow, borderRadius: [4, 4, 0, 0] },
        emphasis: { itemStyle: { color: c.outflow } },
        data: mk(outMap)
      });
    }

    inst.setOption({
      title: titleStyle('年龄段构成', '流入 vs 流出 · 当前方向 ' + dirLabel(curDir), isActive),
      tooltip: {
        trigger: 'axis', confine: true, axisPointer: { type: 'shadow' },
        formatter: function (params) {
          if (!params || !params.length) { return ''; }
          var lines = ['<b>' + params[0].axisValue + '</b>'];
          params.forEach(function (s) {
            lines.push(s.marker + s.seriesName + '：' +
              fmtInt(s.data.value) + ' 次 · ' + pct(s.data.ratio));
          });
          return lines.join('<br/>');
        }
      },
      legend: {
        bottom: 2, itemWidth: 12, itemHeight: 12,
        textStyle: { color: c.muted, fontSize: 12 }
      },
      grid: { left: 6, right: 12, top: 54, bottom: 32, containLabel: true },
      xAxis: {
        type: 'category', data: AGE_ORDER,
        axisLine: { lineStyle: { color: c.border } },
        axisTick: { show: false },
        axisLabel: { color: c.muted, fontSize: 11 }
      },
      yAxis: {
        type: 'value', minInterval: 1,
        splitLine: { lineStyle: { color: c.border, type: 'dashed' } },
        axisLabel: { color: c.faint, fontSize: 11 }
      },
      series: series
    }, true);
  }

  window.renderPortrait = function renderPortrait(containerId, data, ctx) {
    var el = document.getElementById(containerId);
    if (!el) { return; }

    var lib = (ctx && ctx.echarts) || window.echarts;
    var dim = (data && data.dimension) || (ctx && ctx.dimension) || dimFromContainer(containerId);
    var curDir = (ctx && ctx.direction) || (data && data.direction) || 'in';
    var otherDir = curDir === 'in' ? 'out' : 'in';
    var isActive = !!(ctx && ctx.activeDimension && ctx.activeDimension === dim);

    if (!lib || typeof lib.init !== 'function') {
      el.innerHTML =
        '<div class="chart-placeholder">' +
          '<span class="ph-tag">ECharts 未加载</span>' +
          '<span class="ph-meta">无法渲染画像图（' + dim + '）</span>' +
        '</div>';
      return;
    }

    var tk = (el.__pToken = (el.__pToken || 0) + 1);
    var inst = getInst(el, lib);

    function draw(other) {
      if (el.__pToken !== tk) { return; }
      if (dim === 'age_group') {
        var inD = curDir === 'in' ? data : other;
        var outD = curDir === 'in' ? other : data;
        drawAge(inst, inD, outD, curDir, otherDir, isActive);
      } else if (dim === 'is_resident') {
        drawPie(inst, { kind: 'resident', data: data, other: other, curDir: curDir, otherDir: otherDir, isActive: isActive });
      } else {
        drawPie(inst, { kind: 'gender', data: data, other: other, curDir: curDir, otherDir: otherDir, isActive: isActive });
      }
    }

    draw(null);

    if (window.api && typeof window.api.getPortrait === 'function') {
      var otherCtx = Object.assign({}, ctx, { direction: otherDir, dimension: dim });
      window.api.getPortrait(otherCtx)
        .then(function (otherData) { draw(otherData); })
        .catch(function () {});
    }
  };
})();
