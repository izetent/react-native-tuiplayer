declare module 'react-native/Libraries/Types/CodegenTypes' {
  // Minimal stubs for React Native codegen when consuming from TypeScript.
  export type Int32 = number;
  export type Double = number;
  export type Float = number;
  export type WithDefault<T, D> = T | D;
  export type UnsafeObject = object;
  export type BubblingEventHandler<T = unknown> = (event: T) => void;
  export type DirectEventHandler<T = unknown> = (event: T) => void;
}
