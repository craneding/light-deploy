const axios = require('axios');
const req1 = axios.create({ baseURL: 'http://localhost:8080/api' });
console.log(req1.getUri({ url: '/dashboard/stats' }));
console.log(req1.getUri({ url: 'dashboard/stats' }));
const req2 = axios.create({ baseURL: '/api' });
console.log(req2.getUri({ url: '/dashboard/stats' }));
console.log(req2.getUri({ url: 'dashboard/stats' }));
