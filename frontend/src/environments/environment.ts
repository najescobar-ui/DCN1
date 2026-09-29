// Production (AWS) settings. Values are filled in after creating Cognito and API Gateway.
export const environment = {
  production: true,
  /** API Gateway invoke URL, without trailing slash. */
  apiUrl: 'https://w7q276u1ka.execute-api.us-east-1.amazonaws.com',
  cognito: {
    /** User pool issuer (OIDC discovery lives at {authority}/.well-known/openid-configuration). */
    authority: 'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_kMT6rD46S',
    /** Managed login (hosted UI) domain, used for sign-up and logout. */
    domain: 'https://pedidos360-71e3d6.auth.us-east-1.amazoncognito.com',
    clientId: '6vibdqnu5cgvhedrgsvku2bj5b',
    scope:
      'openid email profile pedidos360/productos.read pedidos360/productos.write pedidos360/pedidos.read pedidos360/pedidos.write',
  },
};
