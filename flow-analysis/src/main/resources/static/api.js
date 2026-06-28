/* =============================================================================
 * api.js — 后端 REST API 访问层（纯原生，无依赖）
 * -----------------------------------------------------------------------------
 * 设计约定（与后端契约一致，见 notepad learnings.md）：
 *  - 同源托管：静态资源由 Spring Boot 托管在 :8080，故 BASE 用相对路径 '/api'。
 *  - 统一响应：ApiResp = { code:int, message:string, data:T }，code===200 为成功。
 *    request() 自动解包返回 ApiResp.data；非 200 / 网络异常 → toast 报错并抛出。
 *  - 时间格式：start/end 一律 ISO 'yyyy-MM-ddTHH:mm:ss'（Asia/Shanghai）。
 *  - 枚举小写：granularity=hour|day，direction=in|out，
 *             dimension=gender|age_group|is_resident。
 *  - loading：请求期间引用计数驱动 #loading 遮罩显隐（并发安全）。
 *
 * 暴露：window.api = { getRegions, getTrend, getOd, getPortrait, initData }
 *       window.ui  = { toast, showLoading, hideLoading }（app.js / 图表模块复用）
 * ========================================================================== */
(function () {
  'use strict';

  var BASE = '/api';

  /* ---------- loading 遮罩（引用计数，避免并发请求闪烁） ---------- */
  var _loading = 0;
  function showLoading() {
    _loading++;
    var el = document.getElementById('loading');
    if (el) el.hidden = false;
  }
  function hideLoading() {
    _loading = Math.max(0, _loading - 1);
    if (_loading === 0) {
      var el = document.getElementById('loading');
      if (el) el.hidden = true;
    }
  }

  /* ---------- toast 轻提示 ---------- */
  function toast(message, type) {
    var box = document.getElementById('toast');
    if (!box) { return; }
    var t = document.createElement('div');
    t.className = 'toast toast-' + (type || 'info');
    t.textContent = String(message);
    box.appendChild(t);
    // 进场动画
    requestAnimationFrame(function () { t.classList.add('toast-show'); });
    var ttl = type === 'error' ? 5000 : 3000;
    setTimeout(function () {
      t.classList.remove('toast-show');
      setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 300);
    }, ttl);
  }

  /* ---------- query string 构造（自动跳过 null/undefined/空串，自动编码） ---------- */
  function qs(params) {
    var parts = [];
    Object.keys(params || {}).forEach(function (k) {
      var v = params[k];
      if (v === null || v === undefined || v === '') return;
      parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
    });
    return parts.length ? ('?' + parts.join('&')) : '';
  }

  /* ---------- 核心请求：解析 ApiResp、统一 loading / 报错 ---------- */
  function request(path, opts) {
    showLoading();
    return fetch(BASE + path, opts || {})
      .then(function (res) {
        return res.text().then(function (text) {
          var body = null;
          if (text) { try { body = JSON.parse(text); } catch (e) { /* 非 JSON */ } }
          if (!res.ok) {
            var em = (body && body.message) || ('请求失败（HTTP ' + res.status + '）');
            throw new Error(em);
          }
          // 统一 ApiResp 解包
          if (body && typeof body === 'object' && 'code' in body) {
            if (body.code !== 200) {
              throw new Error(body.message || ('业务错误（code ' + body.code + '）'));
            }
            return body.data;
          }
          return body; // 兜底：非标准结构原样返回
        });
      })
      .catch(function (err) {
        toast(err.message || '网络异常', 'error');
        throw err; // 调用方各自捕获，避免未处理 rejection
      })
      .finally(hideLoading);
  }

  /* ---------- 5 个端点封装 ---------- */
  var api = {
    /** GET /api/regions → [{regionId, regionName, lac, cellCount}] */
    getRegions: function () {
      return request('/regions');
    },

    /** GET /api/flow/trend → [{windowStart, population, inflow, outflow, retained}] */
    getTrend: function (p) {
      return request('/flow/trend' + qs({
        regionId: p.regionId,
        granularity: p.granularity,
        start: p.start,
        end: p.end
      }));
    },

    /** GET /api/flow/od → [{fromRegionId, fromRegionName, toRegionId, toRegionName, flow}] */
    getOd: function (p) {
      return request('/flow/od' + qs({
        granularity: p.granularity,
        start: p.start,
        end: p.end
      }));
    },

    /** GET /api/flow/portrait → {direction, dimension, total, buckets:[{bucket, count, ratio}]} */
    getPortrait: function (p) {
      return request('/flow/portrait' + qs({
        regionId: p.regionId,
        granularity: p.granularity,
        start: p.start,
        end: p.end,
        direction: p.direction,
        dimension: p.dimension
      }));
    },

    /** POST /api/admin/init-data → {seed, userCount, profileRows, dwdRows, ...} */
    initData: function () {
      return request('/admin/init-data', { method: 'POST' });
    }
  };

  window.api = api;
  window.ui = { toast: toast, showLoading: showLoading, hideLoading: hideLoading };
})();
