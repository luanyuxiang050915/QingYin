// a_bogus 签名命令行封装：node sign_cli.js "<query_string>" "<user_agent>"
// 输出签名结果到 stdout
const fs = require('fs');
const path = require('path');
eval(fs.readFileSync(path.join(__dirname, 'douyin.js'), 'utf-8'));

const params = process.argv[2] || '';
const ua = process.argv[3] || '';
process.stdout.write(sign_datail(params, ua));
