"use strict";
// See https://docs.microsoft.com/en-us/azure/active-directory/develop/scenario-spa-app-registration
// You'll need to add the page as a Redirect URL to Azure as a Single Page Application
// noinspection ES6ConvertVarToLetConst - TODO fix this
// Azure Client ID
const azureClientId = "a370fff9-7648-4dbf-b96e-2b4f8d539ac2";
// Origins that we'll not use https://viaversion.github.io/VIAaaS/ as redirect URL
const whitelistedOrigin = [
    "https://via.re.yt.nom.br"
];
// Default CORS Proxy config
var defaultCorsProxy = "https://cors.re.yt.nom.br/";
// Default instance suffix, in format "viaaas.example.com[:25565]", null to use the page hostname;
var defaultInstanceSuffix = null;
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiY29uZmlnLmpzIiwic291cmNlUm9vdCI6IiIsInNvdXJjZXMiOlsiLi4vLi4vLi4vdHlwZXNjcmlwdC9qcy9jb25maWcudHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6IjtBQUFBLG9HQUFvRztBQUNwRyxzRkFBc0Y7QUFDdEYsdURBQXVEO0FBRXZELGtCQUFrQjtBQUNsQixNQUFNLGFBQWEsR0FBVyxzQ0FBc0MsQ0FBQztBQUNyRSxrRkFBa0Y7QUFDbEYsTUFBTSxpQkFBaUIsR0FBYTtJQUNoQywwQkFBMEI7Q0FDN0IsQ0FBQztBQUNGLDRCQUE0QjtBQUM1QixJQUFJLGdCQUFnQixHQUFrQiw0QkFBNEIsQ0FBQztBQUNuRSxrR0FBa0c7QUFDbEcsSUFBSSxxQkFBcUIsR0FBa0IsSUFBSSxDQUFDIn0=