// a_bogus 签名命令行封装（基于 AngelToms 的 ab 算法实现，Apache-2.0）
// 用法: node abogus_cli.js "<query_string>" "<user_agent>"
// 输出: a_bogus 签名（stdout）
const fs = require('fs');
const path = require('path');

// ---- 浏览器环境垫片（算法内部会读取这些全局对象） ----
const UA = process.argv[3] || '';

global.navigator = {
  userAgent: UA,
  platform: 'Win32',
  vendorSubs: {},
};

global.window = {
  innerWidth: 2048,
  innerHeight: 960,
  outerWidth: 2554,
  outerHeight: 1386,
  screen: {
    availWidth: 2560,
    availHeight: 1392,
    width: 2560,
    height: 1440,
    sizeWidth: 2560,
    sizeHeight: 1440,
    platform: 'Win32',
  },
  onwheelx: undefined,
};

// 抑制 G_DEBUG 模式下的大量调试输出
const origLog = console.log;
console.log = function () {};

// 加载算法
eval(fs.readFileSync(path.join(__dirname, 'abogus_sm3.js'), 'utf-8'));
eval(fs.readFileSync(path.join(__dirname, 'abogus_utils.js'), 'utf-8'));

const uri = process.argv[2] || '';
const sig = makeABogus(uri, 0);
process.stdout.write(sig);
