// Local development: API calls go through proxy.conf.json to the Spring Boot services.
export const environment = {
  production: false,
  apiUrl: '',
  cognito: {
    authority: 'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_kMT6rD46S',
    domain: 'https://pedidos360-71e3d6.auth.us-east-1.amazoncognito.com',
    clientId: '6vibdqnu5cgvhedrgsvku2bj5b',
    scope:
      'openid email profile pedidos360/productos.read pedidos360/productos.write pedidos360/pedidos.read pedidos360/pedidos.write',
  },
};
